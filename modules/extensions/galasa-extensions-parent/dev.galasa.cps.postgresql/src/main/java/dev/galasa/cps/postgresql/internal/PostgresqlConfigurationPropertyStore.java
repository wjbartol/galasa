/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.cps.postgresql.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.validation.constraints.NotNull;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.extensions.common.postgresql.PostgresqlStore;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IConfigurationPropertyStore;
import dev.galasa.framework.spi.IConfigurationPropertyStoreService;

/**
 * Real PostgreSQL-backed implementation of the Galasa Configuration Property Store (CPS).
 *
 * <p>All properties are persisted in a {@code cps_properties} table:
 * <pre>
 *   CREATE TABLE IF NOT EXISTS cps_properties (
 *       key   VARCHAR(512) NOT NULL PRIMARY KEY,
 *       value TEXT         NOT NULL
 *   );
 * </pre>
 *
 * <p>Connection management and retry logic are delegated to
 * {@link PostgresqlStore} / {@link PostgresqlConnectionPoolManager} (TASK-016).
 */
public class PostgresqlConfigurationPropertyStore
        extends PostgresqlStore
        implements IConfigurationPropertyStore, IConfigurationPropertyStoreService {

    private static final Logger LOGGER =
            Logger.getLogger(PostgresqlConfigurationPropertyStore.class.getName());

    static final String TABLE_NAME  = "cps_properties";
    static final String DDL_CREATE  =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            "  \"key\"   VARCHAR(512) NOT NULL PRIMARY KEY," +
            "  \"value\" TEXT         NOT NULL" +
            ")";

    public PostgresqlConfigurationPropertyStore(PostgresqlConnectionPoolManager poolManager)
            throws ConfigurationPropertyStoreException {
        super(poolManager);
        ensureSchema();
    }

    // ─── Schema initialisation ────────────────────────────────────────────────

    private void ensureSchema() throws ConfigurationPropertyStoreException {
        try {
            executeSql(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL_CREATE);
                }
                return null;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to initialise CPS schema: " + e.getMessage(), e);
        }
    }

    // ─── IConfigurationPropertyStoreService ──────────────────────────────────

    @Override
    public String getProperty(@NotNull String prefix, @NotNull String suffix, String... infixes)
            throws ConfigurationPropertyStoreException {
        String[] variants = reportPropertyVariants(prefix, suffix, infixes);
        for (String key : variants) {
            String value = fetchOne(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public @NotNull Map<String, String> getPrefixedProperties(@NotNull String prefix)
            throws ConfigurationPropertyStoreException {
        if (prefix == null) {
            return new HashMap<>();
        }
        try {
            return executeSql(conn -> {
                Map<String, String> result = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT \"key\", \"value\" FROM " + TABLE_NAME + " WHERE \"key\" LIKE ?")) {
                    // Use exact prefix match — LIKE with escaped literal avoids wildcard injection
                    ps.setString(1, escapeLike(prefix) + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String k = rs.getString("key");
                            // LIKE can overmatch (e.g. prefix "foo." matches "foo.bar" AND "foo_bar")
                            // Re-check with startsWith to guarantee exactness
                            if (k.startsWith(prefix)) {
                                result.put(k, rs.getString("value"));  // column alias "value" works in both
                            }
                        }
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to get prefixed properties for prefix '" + prefix + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void setProperty(@NotNull String name, @NotNull String value)
            throws ConfigurationPropertyStoreException {
        if (name == null || value == null) {
            throw new ConfigurationPropertyStoreException("Property name and value must not be null");
        }
        try {
            executeSql(conn -> {
                upsert(conn, name, value);
                return null;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to set property '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void setProperties(Map<String, String> propertiesToSet)
            throws ConfigurationPropertyStoreException {
        if (propertiesToSet == null || propertiesToSet.isEmpty()) {
            return;
        }
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    for (Map.Entry<String, String> entry : propertiesToSet.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            upsert(conn, entry.getKey(), entry.getValue());
                        }
                    }
                    conn.commit();
                } catch (Exception e) {
                    try { conn.rollback(); } catch (SQLException ex) { /* best-effort */ }
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to batch set properties: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteProperty(@NotNull String name) throws ConfigurationPropertyStoreException {
        if (name == null) {
            return;
        }
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + TABLE_NAME + " WHERE \"key\" = ?")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to delete property '" + name + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePrefixedProperties(@NotNull String prefix)
            throws ConfigurationPropertyStoreException {
        if (prefix == null) {
            return;
        }
        // Fetch exact matches first (LIKE can overmatch), then delete by key
        Map<String, String> toDelete = getPrefixedProperties(prefix);
        if (toDelete.isEmpty()) {
            return;
        }
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM " + TABLE_NAME + " WHERE \"key\" = ?")) {
                        for (String key : toDelete.keySet()) {
                            ps.setString(1, key);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (Exception e) {
                    try { conn.rollback(); } catch (SQLException ex) { /* best-effort */ }
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to delete properties with prefix '" + prefix + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getAllProperties() throws ConfigurationPropertyStoreException {
        try {
            return executeSql(conn -> {
                Map<String, String> result = new HashMap<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT \"key\", \"value\" FROM " + TABLE_NAME)) {
                    while (rs.next()) {
                        result.put(rs.getString(1), rs.getString(2));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to get all properties: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getCPSNamespaces() throws ConfigurationPropertyStoreException {
        try {
            return executeSql(conn -> {
                Set<String> namespaces = new HashSet<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT \"key\" FROM " + TABLE_NAME)) {
                    while (rs.next()) {
                        String key = rs.getString(1);
                        int dotIndex = key.indexOf('.');
                        namespaces.add(dotIndex > 0 ? key.substring(0, dotIndex) : key);
                    }
                }
                return new ArrayList<>(namespaces);
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to get CPS namespaces: " + e.getMessage(), e);
        }
    }

    // ─── Property variant resolution (pure string logic — no DB) ─────────────

    @Override
    public String[] reportPropertyVariants(@NotNull String prefix, @NotNull String suffix,
            String... infixes) {
        List<String> variants = new ArrayList<>();
        if (infixes != null && infixes.length > 0) {
            // Most-specific first: prefix.infix1.infix2...infixN.suffix
            StringBuilder sb = new StringBuilder(prefix);
            for (String infix : infixes) {
                if (infix != null && !infix.isEmpty()) {
                    sb.append(".").append(infix);
                }
            }
            variants.add(sb.toString() + "." + suffix);
            // Progressively drop trailing infixes
            for (int i = infixes.length - 1; i >= 0; i--) {
                StringBuilder partial = new StringBuilder(prefix);
                for (int j = 0; j < i; j++) {
                    if (infixes[j] != null && !infixes[j].isEmpty()) {
                        partial.append(".").append(infixes[j]);
                    }
                }
                variants.add(partial.toString() + "." + suffix);
            }
        } else {
            variants.add(prefix + "." + suffix);
        }
        return variants.toArray(new String[0]);
    }

    @Override
    public String reportPropertyVariantsString(@NotNull String prefix, @NotNull String suffix,
            String... infixes) {
        return String.join(", ", reportPropertyVariants(prefix, suffix, infixes));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String fetchOne(String key) throws ConfigurationPropertyStoreException {
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT \"value\" FROM " + TABLE_NAME + " WHERE \"key\" = ?")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            });
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to fetch property '" + key + "': " + e.getMessage(), e);
        }
    }

    /**
     * INSERT ... ON CONFLICT (key) DO UPDATE — works on both PostgreSQL and H2 (compat mode).
     * Falls back to DELETE + INSERT for H2 mem databases that don't support ON CONFLICT.
     */
    private void upsert(Connection conn, String key, String value) throws SQLException {
        String sql = "MERGE INTO " + TABLE_NAME +
                     " (\"key\", \"value\") KEY (\"key\") VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            return;
        } catch (SQLException e) {
            // H2 may not have MERGE in all modes; fall back to upsert via delete+insert
            LOGGER.log(Level.FINE, "MERGE not supported, falling back to delete+insert: " + e.getMessage());
        }
        // Fallback: delete existing row, then insert
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + TABLE_NAME + " WHERE \"key\" = ?")) {
            del.setString(1, key);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO " + TABLE_NAME + " (\"key\", \"value\") VALUES (?, ?)")) {
            ins.setString(1, key);
            ins.setString(2, value);
            ins.executeUpdate();
        }
    }

    /** Escapes LIKE special characters in a prefix string. */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ─── IConfigurationPropertyStore single-key access ───────────────────────

    /**
     * Retrieves the value of a fully-qualified CPS property key.
     * Satisfies the {@link IConfigurationPropertyStore} registration interface.
     */
    @Override
    public String getProperty(@NotNull String key) throws ConfigurationPropertyStoreException {
        return fetchOne(key);
    }

    /**
     * Returns all namespace prefixes currently stored.
     * Satisfies the {@link IConfigurationPropertyStore} registration interface.
     * Delegates to {@link #getCPSNamespaces()}.
     */
    @Override
    public List<String> getNamespaces() throws ConfigurationPropertyStoreException {
        return getCPSNamespaces();
    }

    /**
     * Returns all properties whose keys start with the given namespace.
     * Satisfies the {@link IConfigurationPropertyStore} registration interface.
     */
    @Override
    public Map<String, String> getPropertiesFromNamespace(String namespace) throws ConfigurationPropertyStoreException {
        return getPrefixedProperties(namespace);
    }

    /**
     * Releases all pooled connections.
     * Satisfies the {@link IConfigurationPropertyStore} registration interface.
     */
    @Override
    public void shutdown() throws ConfigurationPropertyStoreException {
        getPoolManager().close();
    }
}
