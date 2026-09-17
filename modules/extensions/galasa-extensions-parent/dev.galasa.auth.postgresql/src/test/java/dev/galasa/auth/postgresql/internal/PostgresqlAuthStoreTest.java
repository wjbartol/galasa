/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.auth.AuthStoreException;
import dev.galasa.framework.spi.auth.IInternalAuthToken;
import dev.galasa.framework.spi.auth.IUser;

/**
 * H2 integration tests for the real PostgreSQL-backed Auth store.
 *
 * All tests use an H2 in-memory database — no external PostgreSQL required.
 * These tests exercise real JDBC paths, not in-memory collections.
 */
public class PostgresqlAuthStoreTest {

    private PostgresqlConnectionPoolManager pool;
    private PostgresqlAuthStore store;

    @Before
    public void setUp() throws Exception {
        // Each test gets its own isolated in-memory DB via unique name
        String h2Url = "jdbc:h2:mem:auth_test_" + System.nanoTime()
            + ";DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE";
        pool = new PostgresqlConnectionPoolManager(h2Url, "sa", "");
        pool.setMinPoolSize(1);
        pool.setMaxPoolSize(5);
        store = new PostgresqlAuthStore(pool);
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    // ─── User tests ──────────────────────────────────────────────────────────

    @Test
    public void testCreateAndGetUserByLoginId() throws AuthStoreException {
        store.createUser("alice@example.com", "web-ui", "admin");
        IUser user = store.getUserByLoginId("alice@example.com");
        assertThat(user).isNotNull();
        assertThat(user.getLoginId()).isEqualTo("alice@example.com");
        assertThat(user.getRoleId()).isEqualTo("admin");
    }

    @Test
    public void testGetUserByLoginIdCaseInsensitive() throws AuthStoreException {
        store.createUser("Bob@Example.com", "web-ui", "tester");
        IUser user = store.getUserByLoginId("bob@example.com");
        assertThat(user).isNotNull();
        assertThat(user.getLoginId()).isEqualTo("Bob@Example.com");
    }

    @Test
    public void testGetUserByIdReturnsCorrectUser() throws AuthStoreException {
        store.createUser("carol@example.com", "cli", "viewer");
        IUser byLogin = store.getUserByLoginId("carol@example.com");
        assertThat(byLogin).isNotNull();
        IUser byId = store.getUser(byLogin.getUserNumber());
        assertThat(byId).isNotNull();
        assertThat(byId.getLoginId()).isEqualTo("carol@example.com");
    }

    @Test
    public void testGetUserByLoginIdReturnsNullWhenAbsent() throws AuthStoreException {
        assertThat(store.getUserByLoginId("nobody@example.com")).isNull();
    }

    @Test
    public void testGetAllUsersReturnsAllCreated() throws AuthStoreException {
        store.createUser("u1@example.com", "cli", "role1");
        store.createUser("u2@example.com", "cli", "role2");
        List<IUser> users = store.getAllUsers();
        assertThat(users).extracting(IUser::getLoginId)
            .contains("u1@example.com", "u2@example.com");
    }

    @Test
    public void testDeleteUserRemovesFromDB() throws AuthStoreException {
        store.createUser("todelete@example.com", "cli", "role1");
        IUser user = store.getUserByLoginId("todelete@example.com");
        assertThat(user).isNotNull();
        store.deleteUser(user);
        assertThat(store.getUserByLoginId("todelete@example.com")).isNull();
    }

    @Test
    public void testUpdateUserPersistsChange() throws AuthStoreException {
        store.createUser("update@example.com", "cli", "viewer");
        IUser original = store.getUserByLoginId("update@example.com");
        assertThat(original).isNotNull();
        // Use PostgresqlAuthUser to set a new roleId
        PostgresqlAuthUser updated = new PostgresqlAuthUser(
            original.getUserNumber(), original.getLoginId(), null, "admin");
        IUser result = store.updateUser(updated);
        assertThat(result).isNotNull();
        assertThat(result.getRoleId()).isEqualTo("admin");
    }

    // ─── Token tests ─────────────────────────────────────────────────────────

    @Test
    public void testStoreAndGetAllTokens() throws AuthStoreException {
        store.createUser("tokenuser@example.com", "cli", "tester");
        IUser user = store.getUserByLoginId("tokenuser@example.com");
        store.storeToken("dex-client-1", "My token", (dev.galasa.framework.spi.auth.IInternalUser) user, 30);
        List<IInternalAuthToken> tokens = store.getTokens();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getDexClientId()).isEqualTo("dex-client-1");
    }

    @Test
    public void testGetTokensByLoginId() throws AuthStoreException {
        store.createUser("tokowner@example.com", "cli", "tester");
        IUser user = store.getUserByLoginId("tokowner@example.com");
        store.storeToken("dex-client-2", "Token for owner", (dev.galasa.framework.spi.auth.IInternalUser) user, 7);
        List<IInternalAuthToken> byLogin = store.getTokensByLoginId("tokowner@example.com");
        assertThat(byLogin).hasSize(1);
        assertThat(byLogin.get(0).getDescription()).isEqualTo("Token for owner");
    }

    @Test
    public void testGetTokenByDexClientId() throws AuthStoreException {
        store.createUser("dexuser@example.com", "cli", "tester");
        IUser user = store.getUserByLoginId("dexuser@example.com");
        store.storeToken("unique-dex-client", "desc", (dev.galasa.framework.spi.auth.IInternalUser) user, 14);
        IInternalAuthToken token = store.getTokenByDexClientId("unique-dex-client");
        assertThat(token).isNotNull();
        assertThat(token.getDexClientId()).isEqualTo("unique-dex-client");
    }

    @Test
    public void testDeleteTokenRemovesFromDB() throws AuthStoreException {
        store.createUser("deltok@example.com", "cli", "tester");
        IUser user = store.getUserByLoginId("deltok@example.com");
        store.storeToken("del-client", "to delete", (dev.galasa.framework.spi.auth.IInternalUser) user, 1);
        List<IInternalAuthToken> before = store.getTokensByLoginId("deltok@example.com");
        assertThat(before).hasSize(1);
        store.deleteToken(before.get(0).getTokenId());
        assertThat(store.getTokensByLoginId("deltok@example.com")).isEmpty();
    }

    @Test
    public void testGetTokenByDexClientIdReturnsNullWhenAbsent() throws AuthStoreException {
        assertThat(store.getTokenByDexClientId("nonexistent-client")).isNull();
    }

    @Test
    public void testSchemaIsIdempotent() throws AuthStoreException {
        // creating a second store instance against the same DB must not throw
        new PostgresqlAuthStore(pool);
    }

    @Test
    public void testCreateClientReturnsNonNull() {
        assertThat(store.createClient("my-app")).isNotNull();
    }
}
