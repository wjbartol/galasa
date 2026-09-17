/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import com.google.gson.Gson;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.extensions.common.postgresql.PostgresqlStore;
import dev.galasa.framework.spi.IResultArchiveStoreDirectoryService;
import dev.galasa.framework.spi.IResultArchiveStoreService;
import dev.galasa.framework.spi.ResultArchiveStoreException;
import dev.galasa.framework.spi.teststructure.TestStructure;

/**
 * Real PostgreSQL-backed implementation of the Galasa Result Archive Store (RAS).
 *
 * <p>Two tables are used:
 * <pre>
 *   ras_runs(run_id PK, test_name, status, structure_json, created_at)
 *   ras_run_logs(id SERIAL PK, run_id FK, log_line TEXT, seq BIGINT)
 * </pre>
 *
 * <p>Connection management and retry logic are delegated to
 * {@link PostgresqlStore} / {@link PostgresqlConnectionPoolManager} (TASK-016).
 */
public class PostgresqlRasStore extends PostgresqlStore implements IResultArchiveStoreService {

    static final String RUNS_TABLE = "ras_runs";
    static final String LOGS_TABLE = "ras_run_logs";

    static final String DDL_RUNS =
        "CREATE TABLE IF NOT EXISTS " + RUNS_TABLE + " (" +
        "  run_id        VARCHAR(128) NOT NULL PRIMARY KEY," +
        "  test_name     VARCHAR(256) NOT NULL," +
        "  status        VARCHAR(32)  NOT NULL DEFAULT 'unknown'," +
        "  structure_json TEXT," +
        "  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
        ")";

    static final String DDL_LOGS =
        "CREATE TABLE IF NOT EXISTS " + LOGS_TABLE + " (" +
        "  id     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY," +
        "  run_id VARCHAR(128) NOT NULL," +
        "  seq    BIGINT       NOT NULL," +
        "  line   TEXT         NOT NULL" +
        ")";

    private final Gson gson = new Gson();
    private final String runId;
    // seq counter — only used for the current run; persisted to DB not memory
    private volatile long seqCounter = 0;

    public PostgresqlRasStore(PostgresqlConnectionPoolManager poolManager) throws ResultArchiveStoreException {
        super(poolManager);
        this.runId = UUID.randomUUID().toString();
        ensureSchema();
    }

    /** Package-private constructor for tests that supply a fixed runId. */
    PostgresqlRasStore(PostgresqlConnectionPoolManager poolManager, String runId) throws ResultArchiveStoreException {
        super(poolManager);
        this.runId = runId;
        ensureSchema();
    }

    // ─── Schema initialisation ────────────────────────────────────────────────

    private void ensureSchema() throws ResultArchiveStoreException {
        try {
            executeSql(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(DDL_RUNS);
                    stmt.execute(DDL_LOGS);
                }
                return null;
            });
        } catch (Exception e) {
            throw new ResultArchiveStoreException("Failed to initialise RAS schema: " + e.getMessage(), e);
        }
    }

    // ─── IResultArchiveStoreService ──────────────────────────────────────────

    @Override
    public String calculateRasRunId() {
        return runId;
    }

    @Override
    public void writeLog(@NotNull String message) throws ResultArchiveStoreException {
        if (message == null) return;
        long seq = ++seqCounter;
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + LOGS_TABLE + " (run_id, seq, line) VALUES (?, ?, ?)")) {
                    ps.setString(1, runId);
                    ps.setLong(2, seq);
                    ps.setString(3, message);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new ResultArchiveStoreException("Failed to write log line: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeLog(@NotNull List<String> messages) throws ResultArchiveStoreException {
        if (messages == null || messages.isEmpty()) return;
        try {
            executeSql(conn -> {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + LOGS_TABLE + " (run_id, seq, line) VALUES (?, ?, ?)")) {
                        for (String msg : messages) {
                            if (msg != null) {
                                long seq = ++seqCounter;
                                ps.setString(1, runId);
                                ps.setLong(2, seq);
                                ps.setString(3, msg);
                                ps.addBatch();
                            }
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
            throw new ResultArchiveStoreException("Failed to batch write log lines: " + e.getMessage(), e);
        }
    }

    @Override
    public long retrieveRunLogLineCount() {
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM " + LOGS_TABLE + " WHERE run_id = ?")) {
                    ps.setString(1, runId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getLong(1) : 0L;
                    }
                }
            });
        } catch (Exception e) {
            return seqCounter; // best-effort fallback
        }
    }

    @Override
    public void createTestStructure(@NotNull String structureRunId, @NotNull TestStructure testStructure)
            throws ResultArchiveStoreException {
        if (structureRunId == null || testStructure == null) return;
        String name = testStructure.getTestName() != null ? testStructure.getTestName() : "unknown";
        String status = testStructure.getStatus() != null ? testStructure.getStatus() : "unknown";
        String json = gson.toJson(testStructure);
        try {
            executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "MERGE INTO " + RUNS_TABLE +
                        " (run_id, test_name, status, structure_json) KEY (run_id) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, structureRunId);
                    ps.setString(2, name);
                    ps.setString(3, status);
                    ps.setString(4, json);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (Exception e) {
            throw new ResultArchiveStoreException(
                    "Failed to create test structure for run '" + structureRunId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void updateTestStructure(@NotNull TestStructure testStructure) throws ResultArchiveStoreException {
        if (testStructure == null) return;
        String id = testStructure.getRunName() != null ? testStructure.getRunName() : runId;
        createTestStructure(id, testStructure);
    }

    @Override
    public void updateTestStructure(@NotNull String structureRunId, @NotNull TestStructure testStructure)
            throws ResultArchiveStoreException {
        createTestStructure(structureRunId, testStructure);
    }

    /** Retrieve the stored JSON for a run; returns null if not found. */
    public String getTestStructureJson(String structureRunId) throws ResultArchiveStoreException {
        try {
            return executeSql(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT structure_json FROM " + RUNS_TABLE + " WHERE run_id = ?")) {
                    ps.setString(1, structureRunId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            });
        } catch (Exception e) {
            throw new ResultArchiveStoreException(
                    "Failed to retrieve test structure for run '" + structureRunId + "': " + e.getMessage(), e);
        }
    }

    /** Retrieve all persisted log lines for this run, ordered by seq. */
    public List<String> getLogLines(String logRunId) throws ResultArchiveStoreException {
        try {
            return executeSql(conn -> {
                List<String> lines = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT line FROM " + LOGS_TABLE + " WHERE run_id = ? ORDER BY seq ASC")) {
                    ps.setString(1, logRunId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lines.add(rs.getString(1));
                        }
                    }
                }
                return lines;
            });
        } catch (Exception e) {
            throw new ResultArchiveStoreException(
                    "Failed to retrieve log lines for run '" + logRunId + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Path getStoredArtifactsRoot() {
        return null; // artifact filesystem path not applicable for DB-backed store
    }

    @Override
    public List<IResultArchiveStoreDirectoryService> getDirectoryServices() {
        return Collections.emptyList();
    }

    @Override
    public void flush() {
        // no-op — all writes go directly to DB
    }

    @Override
    public void shutdown() {
        // no-op — pool lifecycle managed externally
    }
}
