/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.extensions.common.postgresql.PostgresqlStore;
import dev.galasa.framework.spi.auth.AuthStoreException;
import dev.galasa.framework.spi.auth.IAuthStore;
import dev.galasa.framework.spi.auth.IFrontEndClient;
import dev.galasa.framework.spi.auth.IInternalAuthToken;
import dev.galasa.framework.spi.auth.IInternalUser;
import dev.galasa.framework.spi.auth.IUser;

/**
 * Real PostgreSQL-backed implementation of the Galasa Auth Store.
 *
 * <p>Two tables are owned by this provider:
 * <pre>
 *   auth_users  (user_id PK, login_id UNIQUE, role_id, created_at)
 *   auth_tokens (token_id PK, dex_client_id, description, owner_login_id, lifespan_days, created_at)
 * </pre>
 *
 * <p>Connection management and retry logic are delegated to
 * {@link PostgresqlStore} / {@link PostgresqlConnectionPoolManager} (TASK-016).
 */
public class PostgresqlAuthStore extends PostgresqlStore implements IAuthStore {

    static final String USERS_TABLE  = "auth_users";
    static final String TOKENS_TABLE = "auth_tokens";

    static final String DDL_USERS =
        "CREATE TABLE IF NOT EXISTS " + USERS_TABLE + " (" +
        "  user_id    VARCHAR(128) NOT NULL PRIMARY KEY," +
        "  login_id   VARCHAR(256) NOT NULL UNIQUE," +
        "  role_id    VARCHAR(128)," +
        "  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
        ")";

    static final String DDL_TOKENS =
        "CREATE TABLE IF NOT EXISTS " + TOKENS_TABLE + " (" +
        "  token_id       VARCHAR(128) NOT NULL PRIMARY KEY," +
        "  dex_client_id  VARCHAR(256)," +
        "  description    TEXT," +
        "  owner_login_id VARCHAR(256)," +
        "  lifespan_days  INT          NOT NULL DEFAULT 30," +
        "  created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
        ")";

    public PostgresqlAuthStore(PostgresqlConnectionPoolManager poolManager) throws AuthStoreException {
        super(poolManager);
        ensureSchema();
    }

    // ─── Schema initialisation ────────────────────────────────────────────────

    private void ensureSchema() throws AuthStoreException {
        try {
            executeSql(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL_USERS);
                    stmt.execute(DDL_TOKENS);
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to initialise Auth schema: " + e.getMessage(), e);
        }
    }

    // ─── IAuthStore — tokens ──────────────────────────────────────────────────

    @Override
    public List<IInternalAuthToken> getTokens() throws AuthStoreException {
        try {
            return executeSql(conn -> {
                List<IInternalAuthToken> result = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT token_id, dex_client_id, description, owner_login_id, lifespan_days FROM " + TOKENS_TABLE)) {
                    while (rs.next()) {
                        result.add(mapToken(rs));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get tokens: " + e.getMessage(), e);
        }
    }

    @Override
    public List<IInternalAuthToken> getTokensByLoginId(String loginId) throws AuthStoreException {
        if (loginId == null) return new ArrayList<>();
        try {
            return executeSql(conn -> {
                List<IInternalAuthToken> result = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT token_id, dex_client_id, description, owner_login_id, lifespan_days FROM " + TOKENS_TABLE +
                        " WHERE LOWER(owner_login_id) = LOWER(?)")) {
                    ps.setString(1, loginId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.add(mapToken(rs));
                        }
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get tokens by login ID: " + e.getMessage(), e);
        }
    }

    @Override
    public void storeToken(String clientId, String description, IInternalUser owner, int tokenLifespanDays)
            throws AuthStoreException {
        String tokenId = UUID.randomUUID().toString();
        String ownerLoginId = owner != null ? owner.getLoginId() : null;
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + TOKENS_TABLE + " (token_id, dex_client_id, description, owner_login_id, lifespan_days)" +
                        " VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, tokenId);
                    ps.setString(2, clientId);
                    ps.setString(3, description);
                    ps.setString(4, ownerLoginId);
                    ps.setInt(5, tokenLifespanDays);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to store token: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteToken(String tokenId) throws AuthStoreException {
        if (tokenId == null) return;
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + TOKENS_TABLE + " WHERE token_id = ?")) {
                    ps.setString(1, tokenId);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to delete token '" + tokenId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public IInternalAuthToken getTokenByDexClientId(String clientId) throws AuthStoreException {
        if (clientId == null) return null;
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT token_id, dex_client_id, description, owner_login_id, lifespan_days FROM " + TOKENS_TABLE +
                        " WHERE dex_client_id = ?")) {
                    ps.setString(1, clientId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? mapToken(rs) : null;
                    }
                }
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get token by Dex client ID: " + e.getMessage(), e);
        }
    }

    // ─── IAuthStore — users ───────────────────────────────────────────────────

    @Override
    public List<IUser> getAllUsers() throws AuthStoreException {
        try {
            return executeSql(conn -> {
                List<IUser> result = new ArrayList<>();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT user_id, login_id, role_id FROM " + USERS_TABLE)) {
                    while (rs.next()) {
                        result.add(new PostgresqlAuthUser(rs.getString(1), rs.getString(2), null, rs.getString(3)));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get all users: " + e.getMessage(), e);
        }
    }

    @Override
    public void createUser(String loginId, String clientName, String roleId) throws AuthStoreException {
        if (loginId == null) return;
        String userId = UUID.randomUUID().toString();
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + USERS_TABLE + " (user_id, login_id, role_id) VALUES (?, ?, ?)")) {
                    ps.setString(1, userId);
                    ps.setString(2, loginId);
                    ps.setString(3, roleId);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to create user '" + loginId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public IUser getUserByLoginId(String loginId) throws AuthStoreException {
        if (loginId == null) return null;
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT user_id, login_id, role_id FROM " + USERS_TABLE + " WHERE LOWER(login_id) = LOWER(?)")) {
                    ps.setString(1, loginId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next()
                            ? new PostgresqlAuthUser(rs.getString(1), rs.getString(2), null, rs.getString(3))
                            : null;
                    }
                }
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get user by login ID: " + e.getMessage(), e);
        }
    }

    @Override
    public IUser getUser(String userNumber) throws AuthStoreException {
        if (userNumber == null) return null;
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT user_id, login_id, role_id FROM " + USERS_TABLE + " WHERE user_id = ?")) {
                    ps.setString(1, userNumber);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next()
                            ? new PostgresqlAuthUser(rs.getString(1), rs.getString(2), null, rs.getString(3))
                            : null;
                    }
                }
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to get user by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public IUser updateUser(IUser user) throws AuthStoreException {
        if (user == null || user.getUserNumber() == null) return user;
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + USERS_TABLE + " SET login_id = ?, role_id = ? WHERE user_id = ?")) {
                    ps.setString(1, user.getLoginId());
                    ps.setString(2, user.getRoleId());
                    ps.setString(3, user.getUserNumber());
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to update user: " + e.getMessage(), e);
        }
        return getUser(user.getUserNumber());
    }

    @Override
    public void deleteUser(IUser user) throws AuthStoreException {
        if (user == null || user.getUserNumber() == null) return;
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + USERS_TABLE + " WHERE user_id = ?")) {
                    ps.setString(1, user.getUserNumber());
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new AuthStoreException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    @Override
    public IFrontEndClient createClient(String clientName) {
        return new PostgresqlFrontEndClient(clientName);
    }

    @Override
    public void shutdown() throws AuthStoreException {
        // pool lifecycle managed externally
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private IInternalAuthToken mapToken(ResultSet rs) throws SQLException {
        String tokenId      = rs.getString(1);
        String dexClientId  = rs.getString(2);
        String description  = rs.getString(3);
        String ownerLoginId = rs.getString(4);
        int    lifespan     = rs.getInt(5);
        IInternalUser owner = ownerLoginId != null
            ? new PostgresqlAuthUser(null, ownerLoginId, null, null)
            : null;
        return new PostgresqlAuthToken(tokenId, dexClientId, description, owner, lifespan);
    }
}
