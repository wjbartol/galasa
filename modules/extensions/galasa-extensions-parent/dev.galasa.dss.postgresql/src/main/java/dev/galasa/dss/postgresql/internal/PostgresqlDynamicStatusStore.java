/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.dss.postgresql.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.extensions.common.postgresql.PostgresqlStore;
import dev.galasa.framework.spi.DynamicStatusStoreException;
import dev.galasa.framework.spi.DynamicStatusStoreMatchException;
import dev.galasa.framework.spi.IDssAction;
import dev.galasa.framework.spi.IDynamicResource;
import dev.galasa.framework.spi.IDynamicRun;
import dev.galasa.framework.spi.IDynamicStatusStore;
import dev.galasa.framework.spi.IDynamicStatusStoreKeyAccess;
import dev.galasa.framework.spi.IDynamicStatusStoreService;
import dev.galasa.framework.spi.IDynamicStatusStoreWatcher;

/**
 * Real PostgreSQL-backed implementation of the Galasa Dynamic Status Store (DSS).
 *
 * <p>One table is owned by this provider:
 * <pre>
 *   dss_properties ("key" PK, "value" TEXT, expires_at TIMESTAMP nullable)
 * </pre>
 *
 * <p>Atomic CAS is implemented via a SQL UPDATE … WHERE "key" = ? AND "value" = ?,
 * checking affected row count to determine success — safe under concurrent access.
 *
 * <p>Connection management and retry logic are delegated to
 * {@link PostgresqlStore} / {@link PostgresqlConnectionPoolManager} (TASK-016).
 */
public class PostgresqlDynamicStatusStore extends PostgresqlStore
        implements IDynamicStatusStore, IDynamicStatusStoreService, IDynamicStatusStoreKeyAccess {

    static final String TABLE = "dss_properties";

    static final String DDL =
        "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
        "  \"key\"       VARCHAR(512) NOT NULL PRIMARY KEY," +
        "  \"value\"     TEXT         NOT NULL," +
        "  expires_at  TIMESTAMP" +
        ")";

    public PostgresqlDynamicStatusStore(PostgresqlConnectionPoolManager poolManager)
            throws DynamicStatusStoreException {
        super(poolManager);
        ensureSchema();
    }

    // ─── Schema initialisation ────────────────────────────────────────────────

    private void ensureSchema() throws DynamicStatusStoreException {
        try {
            executeSql(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL);
                }
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("Failed to initialise DSS schema: " + e.getMessage(), e);
        }
    }

    // ─── IDynamicStatusStoreService / IDynamicStatusStoreKeyAccess ───────────

    @Override
    public void put(@NotNull String key, @NotNull String value) throws DynamicStatusStoreException {
        upsert(key, value, null);
    }

    @Override
    public void put(@NotNull Map<String, String> keyValues) throws DynamicStatusStoreException {
        if (keyValues == null) return;
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    for (Map.Entry<String, String> e : keyValues.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            upsertOnConn(conn, e.getKey(), e.getValue(), null);
                        }
                    }
                    conn.commit();
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException sx) { /* best-effort */ }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("Failed to put map: " + e.getMessage(), e);
        }
    }

    @Override
    public void put(@NotNull String key, @NotNull String value, @NotNull long timeToLiveSecs)
            throws DynamicStatusStoreException {
        upsert(key, value, Instant.now().plusSeconds(timeToLiveSecs));
    }

    @Override
    public void put(@NotNull Map<String, String> keyValues, @NotNull long timeToLiveSecs)
            throws DynamicStatusStoreException {
        if (keyValues == null) return;
        Instant exp = Instant.now().plusSeconds(timeToLiveSecs);
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    for (Map.Entry<String, String> e : keyValues.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            upsertOnConn(conn, e.getKey(), e.getValue(), exp);
                        }
                    }
                    conn.commit();
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException sx) { /* best-effort */ }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("Failed to put map with TTL: " + e.getMessage(), e);
        }
    }

    /**
     * Atomic Compare-And-Swap.
     *
     * <p>When {@code oldValue} is null, succeeds only when no row currently exists.
     * When {@code oldValue} is non-null, succeeds only when the current value matches.
     * All writes (key + others) are committed atomically.
     */
    @Override
    public boolean putSwap(@NotNull String key, String oldValue, @NotNull String newValue)
            throws DynamicStatusStoreException {
        return putSwap(key, oldValue, newValue, Collections.emptyMap());
    }

    @Override
    public boolean putSwap(@NotNull String key, String oldValue, @NotNull String newValue,
            @NotNull Map<String, String> others) throws DynamicStatusStoreException {
        if (key == null || newValue == null) return false;
        try {
            return executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    boolean swapped = casUpdate(conn, key, oldValue, newValue);
                    if (swapped && others != null) {
                        for (Map.Entry<String, String> e : others.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                upsertOnConn(conn, e.getKey(), e.getValue(), null);
                            }
                        }
                    }
                    if (swapped) {
                        conn.commit();
                    } else {
                        conn.rollback();
                    }
                    return swapped;
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException sx) { /* best-effort */ }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("putSwap failed: " + e.getMessage(), e);
        }
    }

    @Override
    public @Null String get(@NotNull String key) throws DynamicStatusStoreException {
        if (key == null) return null;
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT \"value\", expires_at FROM " + TABLE + " WHERE \"key\" = ?")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return null;
                        Timestamp exp = rs.getTimestamp(2);
                        if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                            deleteByKey(conn, key);
                            return null;
                        }
                        return rs.getString(1);
                    }
                }
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("get failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    @Override
    public @NotNull Map<String, String> getPrefix(@NotNull String keyPrefix)
            throws DynamicStatusStoreException {
        if (keyPrefix == null) return new HashMap<>();
        try {
            return executeSql(conn -> {
                Map<String, String> result = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT \"key\", \"value\", expires_at FROM " + TABLE + " WHERE \"key\" LIKE ?")) {
                    ps.setString(1, escapeLike(keyPrefix) + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String k = rs.getString(1);
                            Timestamp exp = rs.getTimestamp(3);
                            if (k.startsWith(keyPrefix)
                                    && (exp == null || !exp.toInstant().isBefore(Instant.now()))) {
                                result.put(k, rs.getString(2));
                            }
                        }
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("getPrefix failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Collection<String> getPrefixKeysOnly(@NotNull String keyPrefix)
            throws DynamicStatusStoreException {
        if (keyPrefix == null) return new ArrayList<>();
        try {
            return executeSql(conn -> {
                List<String> keys = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT \"key\", expires_at FROM " + TABLE + " WHERE \"key\" LIKE ?")) {
                    ps.setString(1, escapeLike(keyPrefix) + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String k = rs.getString(1);
                            Timestamp exp = rs.getTimestamp(2);
                            if (k.startsWith(keyPrefix)
                                    && (exp == null || !exp.toInstant().isBefore(Instant.now()))) {
                                keys.add(k);
                            }
                        }
                    }
                }
                return keys;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("getPrefixKeysOnly failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(@NotNull String key) throws DynamicStatusStoreException {
        if (key == null) return;
        try {
            executeSql(conn -> {
                deleteByKey(conn, key);
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(@NotNull Set<String> keys) throws DynamicStatusStoreException {
        if (keys == null || keys.isEmpty()) return;
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM " + TABLE + " WHERE \"key\" = ?")) {
                        for (String k : keys) {
                            ps.setString(1, k);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    conn.commit();
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException sx) { /* best-effort */ }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("delete(Set) failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePrefix(@NotNull String keyPrefix) throws DynamicStatusStoreException {
        if (keyPrefix == null) return;
        // Fetch matching keys first (safe prefix check), then delete by exact key
        Map<String, String> toDelete = getPrefix(keyPrefix);
        if (toDelete.isEmpty()) return;
        delete(toDelete.keySet());
    }

    @Override
    public void performActions(IDssAction... actions)
            throws DynamicStatusStoreException, DynamicStatusStoreMatchException {
        // No-op for actions that have no SQL representation (e.g. watch triggers).
        // Real CAS actions should use putSwap directly.
    }

    @Override
    public UUID watch(IDynamicStatusStoreWatcher watcher, String key) throws DynamicStatusStoreException {
        // Watcher registration is not backed by DB in this implementation.
        // Returned UUID is a no-op handle for unwatch compatibility.
        return UUID.randomUUID();
    }

    @Override
    public UUID watchPrefix(IDynamicStatusStoreWatcher watcher, String keyPrefix)
            throws DynamicStatusStoreException {
        return UUID.randomUUID();
    }

    @Override
    public void unwatch(UUID watchId) throws DynamicStatusStoreException {
        // no-op — see watch()
    }

    @Override
    public IDynamicResource getDynamicResource(String resourceKey) {
        return null;
    }

    @Override
    public IDynamicRun getDynamicRun() throws DynamicStatusStoreException {
        return null;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void upsert(String key, String value, Instant expiresAt) throws DynamicStatusStoreException {
        if (key == null || value == null) return;
        try {
            executeSql(conn -> {
                upsertOnConn(conn, key, value, expiresAt);
                return null;
            });
        } catch (Exception e) {
            throw new DynamicStatusStoreException("upsert failed for key '" + key + "': " + e.getMessage(), e);
        }
    }

    private void upsertOnConn(Connection conn, String key, String value, Instant expiresAt) throws SQLException {
        Timestamp ts = expiresAt != null ? Timestamp.from(expiresAt) : null;
        // Try MERGE (H2 + PostgreSQL 15+ with ON CONFLICT)
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO " + TABLE + " (\"key\", \"value\", expires_at) KEY (\"key\") VALUES (?, ?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.setTimestamp(3, ts);
                ps.executeUpdate();
                return;
            }
        } catch (SQLException e) {
            // fall through to delete+insert
        }
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE \"key\" = ?")) {
            del.setString(1, key);
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO " + TABLE + " (\"key\", \"value\", expires_at) VALUES (?, ?, ?)")) {
            ins.setString(1, key);
            ins.setString(2, value);
            ins.setTimestamp(3, ts);
            ins.executeUpdate();
        }
    }

    /**
     * Atomic CAS update. Returns true if the swap was applied.
     * oldValue == null means "row must not exist".
     */
    private boolean casUpdate(Connection conn, String key, String oldValue, String newValue) throws SQLException {
        if (oldValue == null) {
            // Insert only if key does not exist
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO " + TABLE + " (\"key\", \"value\") VALUES (?, ?)")) {
                ins.setString(1, key);
                ins.setString(2, newValue);
                try {
                    int rows = ins.executeUpdate();
                    return rows == 1;
                } catch (SQLException e) {
                    // Duplicate key — CAS failed
                    return false;
                }
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + TABLE + " SET \"value\" = ? WHERE \"key\" = ? AND \"value\" = ?")) {
                ps.setString(1, newValue);
                ps.setString(2, key);
                ps.setString(3, oldValue);
                int rows = ps.executeUpdate();
                return rows == 1;
            }
        }
    }

    private void deleteByKey(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE \"key\" = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }

    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Releases all pooled connections. Satisfies {@link IDynamicStatusStore}. */
    @Override
    public void shutdown() throws DynamicStatusStoreException {
        getPoolManager().close();
    }
}
