/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

import java.sql.Connection;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base store implementation providing Connection management, health probes,
 * and retry processing for PostgreSQL operations.
 */
public abstract class PostgresqlStore {

    private static final Logger LOGGER = Logger.getLogger(PostgresqlStore.class.getName());

    private final PostgresqlConnectionPoolManager poolManager;
    private int maxRetries = 3;
    private long retryDelayMs = 1000;

    public PostgresqlStore(PostgresqlConnectionPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    public PostgresqlConnectionPoolManager getPoolManager() {
        return poolManager;
    }

    /**
     * Executes a Callable operation with retry logic.
     */
    public <T> T executeWithRetry(Callable<T> operation) throws Exception {
        int attempt = 0;
        Exception lastException = null;
        while (attempt < maxRetries) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                attempt++;
                LOGGER.log(Level.WARNING, String.format("PostgreSQL operation attempt %d failed: %s", attempt, e.getMessage()));
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelayMs * attempt);
                }
            }
        }
        throw lastException;
    }

    /**
     * Obtains a Connection from the pool, executes an operation, and returns the Connection safely.
     */
    public <T> T executeSql(SqlOperation<T> operation) throws Exception {
        return executeWithRetry(() -> {
            Connection conn = poolManager.getConnection();
            try {
                return operation.execute(conn);
            } finally {
                poolManager.releaseConnection(conn);
            }
        });
    }

    /**
     * Functional interface for JDBC SQL operations.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute(Connection conn) throws Exception;
    }

    /**
     * Health probe checking real connection status via SELECT 1 query.
     */
    public boolean pingHealthProbe() {
        return poolManager != null && poolManager.checkConnectionHealth();
    }
}
