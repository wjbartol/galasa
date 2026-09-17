/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.dss.postgresql.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.DynamicStatusStoreException;

/**
 * H2 integration tests for the real PostgreSQL-backed DSS store.
 *
 * All tests use an H2 in-memory database — no external PostgreSQL required.
 * These tests exercise real JDBC paths including atomic CAS and TTL.
 */
public class PostgresqlDssTest {

    private PostgresqlConnectionPoolManager pool;
    private PostgresqlDynamicStatusStore dss;

    @Before
    public void setUp() throws Exception {
        // Each test gets its own isolated in-memory DB
        String h2Url = "jdbc:h2:mem:dss_test_" + System.nanoTime()
            + ";DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE";
        pool = new PostgresqlConnectionPoolManager(h2Url, "sa", "");
        pool.setMinPoolSize(1);
        pool.setMaxPoolSize(5);
        dss = new PostgresqlDynamicStatusStore(pool);
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    public void testPutAndGet() throws DynamicStatusStoreException {
        dss.put("ns.key1", "value1");
        assertThat(dss.get("ns.key1")).isEqualTo("value1");
    }

    @Test
    public void testGetReturnsNullWhenAbsent() throws DynamicStatusStoreException {
        assertThat(dss.get("no.such.key")).isNull();
    }

    @Test
    public void testPutOverwritesExisting() throws DynamicStatusStoreException {
        dss.put("over.key", "first");
        dss.put("over.key", "second");
        assertThat(dss.get("over.key")).isEqualTo("second");
    }

    @Test
    public void testPutMap() throws DynamicStatusStoreException {
        Map<String, String> map = new HashMap<>();
        map.put("map.a", "alpha");
        map.put("map.b", "beta");
        dss.put(map);
        assertThat(dss.get("map.a")).isEqualTo("alpha");
        assertThat(dss.get("map.b")).isEqualTo("beta");
    }

    @Test
    public void testPutWithTtlAndGet() throws DynamicStatusStoreException {
        dss.put("ttl.key", "alive", 3600L);
        assertThat(dss.get("ttl.key")).isEqualTo("alive");
    }

    @Test
    public void testGetPrefixReturnsMatchingKeys() throws DynamicStatusStoreException {
        dss.put("prefix.a", "1");
        dss.put("prefix.b", "2");
        dss.put("other.c", "3");
        Map<String, String> result = dss.getPrefix("prefix.");
        assertThat(result).containsOnlyKeys("prefix.a", "prefix.b");
        assertThat(result).containsEntry("prefix.a", "1");
    }

    @Test
    public void testGetPrefixKeysOnlyReturnsKeys() throws DynamicStatusStoreException {
        dss.put("keys.x", "val");
        dss.put("keys.y", "val");
        Collection<String> keys = dss.getPrefixKeysOnly("keys.");
        assertThat(keys).containsExactlyInAnyOrder("keys.x", "keys.y");
    }

    @Test
    public void testDeleteRemovesKey() throws DynamicStatusStoreException {
        dss.put("del.key", "value");
        dss.delete("del.key");
        assertThat(dss.get("del.key")).isNull();
    }

    @Test
    public void testDeleteSetOfKeys() throws DynamicStatusStoreException {
        dss.put("set.a", "v1");
        dss.put("set.b", "v2");
        dss.delete(Set.of("set.a", "set.b"));
        assertThat(dss.get("set.a")).isNull();
        assertThat(dss.get("set.b")).isNull();
    }

    @Test
    public void testDeletePrefixRemovesAllMatching() throws DynamicStatusStoreException {
        dss.put("pref.a", "1");
        dss.put("pref.b", "2");
        dss.put("other.c", "3");
        dss.deletePrefix("pref.");
        assertThat(dss.get("pref.a")).isNull();
        assertThat(dss.get("pref.b")).isNull();
        assertThat(dss.get("other.c")).isEqualTo("3");
    }

    // ─── CAS tests ────────────────────────────────────────────────────────────

    @Test
    public void testPutSwapSucceedsWhenKeyAbsentAndOldValueNull() throws DynamicStatusStoreException {
        boolean swapped = dss.putSwap("cas.key", null, "acquired");
        assertThat(swapped).isTrue();
        assertThat(dss.get("cas.key")).isEqualTo("acquired");
    }

    @Test
    public void testPutSwapFailsWhenKeyExistsAndOldValueNull() throws DynamicStatusStoreException {
        dss.put("cas.exist", "taken");
        boolean swapped = dss.putSwap("cas.exist", null, "new");
        assertThat(swapped).isFalse();
        assertThat(dss.get("cas.exist")).isEqualTo("taken");
    }

    @Test
    public void testPutSwapSucceedsWhenOldValueMatches() throws DynamicStatusStoreException {
        dss.put("cas.match", "old");
        boolean swapped = dss.putSwap("cas.match", "old", "new");
        assertThat(swapped).isTrue();
        assertThat(dss.get("cas.match")).isEqualTo("new");
    }

    @Test
    public void testPutSwapFailsWhenOldValueMismatch() throws DynamicStatusStoreException {
        dss.put("cas.mismatch", "current");
        boolean swapped = dss.putSwap("cas.mismatch", "wrong", "new");
        assertThat(swapped).isFalse();
        assertThat(dss.get("cas.mismatch")).isEqualTo("current");
    }

    @Test
    public void testPutSwapWithOthersAppliesAtomically() throws DynamicStatusStoreException {
        Map<String, String> others = new HashMap<>();
        others.put("extra.a", "1");
        others.put("extra.b", "2");
        boolean swapped = dss.putSwap("lock.key", null, "locked", others);
        assertThat(swapped).isTrue();
        assertThat(dss.get("lock.key")).isEqualTo("locked");
        assertThat(dss.get("extra.a")).isEqualTo("1");
        assertThat(dss.get("extra.b")).isEqualTo("2");
    }

    @Test
    public void testSchemaIsIdempotent() throws DynamicStatusStoreException {
        // A second store on the same pool/DB must not throw
        new PostgresqlDynamicStatusStore(pool);
    }
}
