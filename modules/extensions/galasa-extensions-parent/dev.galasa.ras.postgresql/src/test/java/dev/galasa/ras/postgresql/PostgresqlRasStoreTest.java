/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.ResultArchiveStoreException;
import dev.galasa.framework.spi.teststructure.TestStructure;

/**
 * H2 integration tests for the real PostgreSQL-backed RAS store.
 *
 * All tests use an H2 in-memory database so no external PostgreSQL instance is required.
 * These tests exercise real JDBC write/read paths — not in-memory collections.
 */
public class PostgresqlRasStoreTest {

    private static final String H2_URL =
        "jdbc:h2:mem:ras_test_" + System.nanoTime() +
        ";DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE";

    private PostgresqlConnectionPoolManager pool;

    @Before
    public void setUp() throws Exception {
        pool = new PostgresqlConnectionPoolManager(H2_URL, "sa", "");
        pool.setMinPoolSize(1);
        pool.setMaxPoolSize(5);
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    public void testCalculateRasRunIdIsUnique() throws ResultArchiveStoreException {
        PostgresqlRasStore store1 = new PostgresqlRasStore(pool, "run-aaa");
        PostgresqlRasStore store2 = new PostgresqlRasStore(pool, "run-bbb");
        assertThat(store1.calculateRasRunId()).isEqualTo("run-aaa");
        assertThat(store2.calculateRasRunId()).isEqualTo("run-bbb");
        assertThat(store1.calculateRasRunId()).isNotEqualTo(store2.calculateRasRunId());
    }

    @Test
    public void testWriteSingleLogLineAndRetrieve() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-001");
        store.writeLog("Hello from PostgreSQL RAS");
        List<String> lines = store.getLogLines("run-001");
        assertThat(lines).containsExactly("Hello from PostgreSQL RAS");
    }

    @Test
    public void testWriteMultipleLogLinesOrdered() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-002");
        store.writeLog(Arrays.asList("Line A", "Line B", "Line C"));
        List<String> lines = store.getLogLines("run-002");
        assertThat(lines).containsExactly("Line A", "Line B", "Line C");
    }

    @Test
    public void testRetrieveRunLogLineCount() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-003");
        store.writeLog("one");
        store.writeLog(Arrays.asList("two", "three"));
        assertThat(store.retrieveRunLogLineCount()).isEqualTo(3L);
    }

    @Test
    public void testLogLinesIsolatedByRunId() throws ResultArchiveStoreException {
        PostgresqlRasStore storeA = new PostgresqlRasStore(pool, "run-A");
        PostgresqlRasStore storeB = new PostgresqlRasStore(pool, "run-B");
        storeA.writeLog("from A");
        storeB.writeLog("from B");
        assertThat(storeA.getLogLines("run-A")).containsExactly("from A");
        assertThat(storeB.getLogLines("run-B")).containsExactly("from B");
    }

    @Test
    public void testCreateAndRetrieveTestStructure() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-004");
        TestStructure ts = new TestStructure();
        ts.setRunName("run-004");
        ts.setTestName("dev.galasa.test.MyTest");
        ts.setStatus("passed");
        store.createTestStructure("run-004", ts);
        String json = store.getTestStructureJson("run-004");
        assertThat(json).isNotNull();
        assertThat(json).contains("MyTest");
        assertThat(json).contains("passed");
    }

    @Test
    public void testUpdateTestStructureOverwrites() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-005");
        TestStructure ts1 = new TestStructure();
        ts1.setRunName("run-005");
        ts1.setTestName("TestA");
        ts1.setStatus("running");
        store.createTestStructure("run-005", ts1);

        TestStructure ts2 = new TestStructure();
        ts2.setRunName("run-005");
        ts2.setTestName("TestA");
        ts2.setStatus("passed");
        store.updateTestStructure(ts2);

        String json = store.getTestStructureJson("run-005");
        assertThat(json).contains("passed");
    }

    @Test
    public void testGetTestStructureJsonReturnsNullWhenAbsent() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-006");
        assertThat(store.getTestStructureJson("no-such-run")).isNull();
    }

    @Test
    public void testSchemaIsIdempotent() throws ResultArchiveStoreException {
        // constructing two stores against the same pool / DB should not throw
        new PostgresqlRasStore(pool, "run-007");
        new PostgresqlRasStore(pool, "run-008");
    }

    @Test
    public void testGetDirectoryServicesReturnsEmpty() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-009");
        assertThat(store.getDirectoryServices()).isEmpty();
    }

    @Test
    public void testGetStoredArtifactsRootIsNull() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-010");
        assertThat(store.getStoredArtifactsRoot()).isNull();
    }

    @Test
    public void testWriteNullLogNoOp() throws ResultArchiveStoreException {
        PostgresqlRasStore store = new PostgresqlRasStore(pool, "run-011");
        store.writeLog((String) null); // must not throw
        assertThat(store.getLogLines("run-011")).isEmpty();
    }
}
