/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for PostgreSQL infrastructure using H2 in-memory database
 * (PostgreSQL compatibility mode).  These tests exercise real JDBC connection
 * acquisition, schema DDL execution, INSERT/SELECT round-trips, connection
 * pool health checks, and unavailability handling – all without requiring an
 * external database server.
 *
 * Gate compliance (SPECIFICATION_WORKFLOW.md):
 *   Gate 3: tests invoke the actual code path (real JDBC Connection, real SQL)
 *   Gate 4: each acceptance criterion from spec-016 is covered by a test case
 *   Gate 5: unavailability is proven to fail, not PASS
 *   Gate 6: H2 driver declared in build.gradle testImplementation
 */
public class PostgresqlLiveIntegrationTest {

    // H2 in PostgreSQL-compatibility mode. Named DB so pool warm-up connections
    // share the same in-memory instance within this test class.
    private static final String H2_URL =
        "jdbc:h2:mem:galasa_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    private PostgresqlConnectionPoolManager pool;
    private PostgresqlSchemaInitializer schemaInit;

    @Before
    public void setUp() throws SQLException {
        pool = new PostgresqlConnectionPoolManager(H2_URL, "sa", "");
        pool.setMinPoolSize(1);
        pool.setMaxPoolSize(5);
        schemaInit = new PostgresqlSchemaInitializer(pool);
        // Initialize schema once before each test (idempotent — IF NOT EXISTS)
        schemaInit.initializeSchema();
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    // -------------------------------------------------------------------------
    // Gate 3 / 4: Schema initialisation creates all 6 required tables
    // -------------------------------------------------------------------------
    @Test
    public void testSchemaInitializationCreatesAllRequiredTables() throws Exception {
        PostgresqlStore store = new PostgresqlStore(pool) {};

        // Query H2 information_schema to confirm all 6 tables were created
        String[] expectedTables = {
            "RAS_RUNS", "RAS_ARTIFACTS", "CPS_PROPERTIES",
            "DSS_LOCKS", "AUTH_TOKENS", "AUTH_CREDENTIALS"
        };

        store.executeSql(conn -> {
            for (String table : expectedTables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE UPPER(table_name) = ?")) {
                    ps.setString(1, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1))
                            .as("Table %s must exist after schema init", table)
                            .isEqualTo(1);
                    }
                }
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Gate 3 / 4: Real JDBC INSERT and SELECT round-trip against ras_runs
    // -------------------------------------------------------------------------
    @Test
    public void testRealInsertAndSelectRoundTrip() throws Exception {
        PostgresqlStore store = new PostgresqlStore(pool) {};

        final String runId   = "RUN-AGENT3-TEST-001";
        final String testName = "dev.galasa.test.Agent3IntegrationTest";
        final String status  = "PASSED";
        final long   durationMs = 4200L;
        final long   artifactBytes = 98304L;

        // INSERT
        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ras_runs " +
                    "(run_id, test_name, status, run_duration_ms, artifact_size_bytes) " +
                    "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, runId);
                ps.setString(2, testName);
                ps.setString(3, status);
                ps.setLong(4, durationMs);
                ps.setLong(5, artifactBytes);
                int rowsInserted = ps.executeUpdate();
                assertThat(rowsInserted).isEqualTo(1);
            }
            return null;
        });

        // SELECT back and assert values
        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT test_name, status, run_duration_ms, artifact_size_bytes " +
                    "FROM ras_runs WHERE run_id = ?")) {
                ps.setString(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("Row must exist for run_id %s", runId).isTrue();
                    assertThat(rs.getString("test_name")).isEqualTo(testName);
                    assertThat(rs.getString("status")).isEqualTo(status);
                    assertThat(rs.getLong("run_duration_ms")).isEqualTo(durationMs);
                    assertThat(rs.getLong("artifact_size_bytes")).isEqualTo(artifactBytes);
                }
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Gate 3 / 4: Connection pool health probe returns true on live DB
    // -------------------------------------------------------------------------
    @Test
    public void testConnectionHealthProbeReturnsTrueOnLiveDatabase() {
        PostgresqlStore store = new PostgresqlStore(pool) {};
        assertThat(store.pingHealthProbe())
            .as("Health probe must return true when H2 in-memory DB is reachable")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // Gate 5: Unavailability — connecting to an unreachable port must throw
    //         SQLException, NOT silently return PASS
    // -------------------------------------------------------------------------
    @Test
    public void testOfflineDatabaseUnavailabilityThrowsSQLException() {
        // Port 59999 has no listener — connection must fail
        PostgresqlConnectionPoolManager offlinePool =
            new PostgresqlConnectionPoolManager("localhost", 59999, "galasa", "user", "pass");
        offlinePool.setMinPoolSize(0); // don't pre-warm; let getConnection() trigger

        assertThatThrownBy(offlinePool::getConnection)
            .as("Connecting to unreachable port 59999 must throw SQLException, not return PASS")
            .isInstanceOf(SQLException.class);

        // Health probe on the offline pool must return false
        PostgresqlStore offlineStore = new PostgresqlStore(offlinePool) {};
        assertThat(offlineStore.pingHealthProbe())
            .as("Health probe must return false when database is unreachable")
            .isFalse();

        offlinePool.close();
    }

    // -------------------------------------------------------------------------
    // Gate 4: CPS properties INSERT / SELECT round-trip
    // -------------------------------------------------------------------------
    @Test
    public void testCpsPropertyInsertAndSelect() throws Exception {
        PostgresqlStore store = new PostgresqlStore(pool) {};

        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cps_properties (property_key, property_value) VALUES (?, ?)")) {
                ps.setString(1, "galasa.test.endpoint");
                ps.setString(2, "http://simbank:2080");
                ps.executeUpdate();
            }
            return null;
        });

        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT property_value FROM cps_properties WHERE property_key = ?")) {
                ps.setString(1, "galasa.test.endpoint");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("property_value")).isEqualTo("http://simbank:2080");
                }
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Gate 4: idempotent schema init — calling initializeSchema() twice must not
    //         throw or corrupt existing data
    // -------------------------------------------------------------------------
    @Test
    public void testSchemaInitIsIdempotent() throws Exception {
        // Insert a row first
        PostgresqlStore store = new PostgresqlStore(pool) {};
        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ras_runs (run_id, test_name, status) VALUES (?, ?, ?)")) {
                ps.setString(1, "RUN-IDEMPOTENT-001");
                ps.setString(2, "dev.galasa.test.Idempotent");
                ps.setString(3, "RUNNING");
                ps.executeUpdate();
            }
            return null;
        });

        // Re-run schema init — must not throw or delete the existing row
        schemaInit.initializeSchema();

        store.executeSql(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM ras_runs WHERE run_id = ?")) {
                ps.setString(1, "RUN-IDEMPOTENT-001");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                        .as("Pre-existing row must survive a second schema init call")
                        .isTrue();
                    assertThat(rs.getString("status")).isEqualTo("RUNNING");
                }
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Gate 4: executeWithRetry recovers from transient failures
    // -------------------------------------------------------------------------
    @Test
    public void testExecuteWithRetryRecoversFromTransientFailure() throws Exception {
        PostgresqlStore store = new PostgresqlStore(pool) {};
        int[] attempts = {0};

        String result = store.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] < 2) {
                throw new RuntimeException("Simulated transient failure on attempt " + attempts[0]);
            }
            return "RECOVERED";
        });

        assertThat(result).isEqualTo("RECOVERED");
        assertThat(attempts[0]).isEqualTo(2);
    }
}
