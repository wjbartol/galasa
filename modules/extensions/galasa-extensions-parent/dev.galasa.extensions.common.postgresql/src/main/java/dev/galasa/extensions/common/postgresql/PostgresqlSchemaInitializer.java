/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automates schema creation and migrations for Galasa PostgreSQL tables.
 */
public class PostgresqlSchemaInitializer {

    private static final Logger LOGGER = Logger.getLogger(PostgresqlSchemaInitializer.class.getName());

    private final PostgresqlConnectionPoolManager poolManager;

    public PostgresqlSchemaInitializer(PostgresqlConnectionPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    /**
     * Creates all required tables for RAS, CPS, DSS, and Auth providers if they do not already exist.
     */
    public synchronized void initializeSchema() throws SQLException {
        Connection conn = poolManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // 1. RAS Runs Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ras_runs (" +
                "  run_id VARCHAR(128) PRIMARY KEY, " +
                "  test_name VARCHAR(256) NOT NULL, " +
                "  status VARCHAR(32) NOT NULL, " +
                "  run_duration_ms BIGINT DEFAULT 0, " +
                "  artifact_size_bytes BIGINT DEFAULT 0, " +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 2. RAS Artifacts Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ras_artifacts (" +
                "  artifact_id VARCHAR(128) PRIMARY KEY, " +
                "  run_id VARCHAR(128) NOT NULL, " +
                "  artifact_name VARCHAR(256) NOT NULL, " +
                "  content BYTEA, " +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 3. CPS Configuration Properties Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS cps_properties (" +
                "  property_key VARCHAR(256) PRIMARY KEY, " +
                "  property_value TEXT, " +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 4. DSS Distributed Locks Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS dss_locks (" +
                "  lock_key VARCHAR(256) PRIMARY KEY, " +
                "  holder_id VARCHAR(256) NOT NULL, " +
                "  expires_at TIMESTAMP NOT NULL, " +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // 5. Auth Tokens & Users Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS auth_tokens (" +
                "  token_id VARCHAR(128) PRIMARY KEY, " +
                "  user_id VARCHAR(128) NOT NULL, " +
                "  token_data TEXT NOT NULL, " +
                "  expires_at TIMESTAMP, " +
                "  is_revoked BOOLEAN DEFAULT FALSE" +
                ")"
            );

            // 6. Auth Credentials Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS auth_credentials (" +
                "  cred_id VARCHAR(128) PRIMARY KEY, " +
                "  cred_type VARCHAR(64) NOT NULL, " +
                "  cred_payload TEXT NOT NULL, " +
                "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            LOGGER.info("PostgreSQL schema initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize PostgreSQL schema DDL", e);
            throw e;
        } finally {
            poolManager.releaseConnection(conn);
        }
    }
}
