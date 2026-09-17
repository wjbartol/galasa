/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded JDBC Connection Pool Manager for PostgreSQL and PostgreSQL-compatible engines.
 * Manages active JDBC connections, pool limits, reconnection, and health checks.
 */
public class PostgresqlConnectionPoolManager {

    private static final Logger LOGGER = Logger.getLogger(PostgresqlConnectionPoolManager.class.getName());

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private int minPoolSize = 2;
    private int maxPoolSize = 10;
    private int connectionTimeoutMs = 30000;
    private boolean sslEnabled = false;

    private final BlockingQueue<Connection> connectionPool;
    private boolean initialized = false;
    private boolean isClosed = false;

    public PostgresqlConnectionPoolManager(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.connectionPool = new ArrayBlockingQueue<>(100);
    }

    public PostgresqlConnectionPoolManager(String host, int port, String database, String username, String password) {
        this(String.format("jdbc:postgresql://%s:%d/%s", host, port, database), username, password);
    }

    /**
     * Convenience factory: parses credentials embedded in a JDBC URL of the form
     * {@code jdbc:postgresql://user:password@host:port/db}.
     * If no userinfo is present, username and password are {@code null}.
     */
    public static PostgresqlConnectionPoolManager fromJdbcUrl(String jdbcUrl) {
        // Extract credentials from jdbc:postgresql://user:password@host:port/db
        String username = null;
        String password = null;
        String cleanUrl = jdbcUrl;

        // Find the authority section: everything between :// and the next /
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd >= 0) {
            int authorityStart = schemeEnd + 3;
            int authorityEnd = jdbcUrl.indexOf('/', authorityStart);
            if (authorityEnd < 0) authorityEnd = jdbcUrl.length();
            String authority = jdbcUrl.substring(authorityStart, authorityEnd);
            int atIdx = authority.lastIndexOf('@');
            if (atIdx >= 0) {
                String userInfo = authority.substring(0, atIdx);
                String host = authority.substring(atIdx + 1);
                int colonIdx = userInfo.indexOf(':');
                if (colonIdx >= 0) {
                    username = userInfo.substring(0, colonIdx);
                    password = userInfo.substring(colonIdx + 1);
                } else {
                    username = userInfo;
                }
                // Rebuild URL without embedded credentials
                cleanUrl = jdbcUrl.substring(0, authorityStart) + host +
                           jdbcUrl.substring(authorityEnd);
            }
        }

        return new PostgresqlConnectionPoolManager(cleanUrl, username, password);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getMinPoolSize() {
        return minPoolSize;
    }

    public void setMinPoolSize(int minPoolSize) {
        this.minPoolSize = minPoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Properties buildConnectionProperties() {
        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        if (sslEnabled) {
            props.setProperty("ssl", "true");
        }
        props.setProperty("loginTimeout", String.valueOf(connectionTimeoutMs / 1000));
        return props;
    }

    /**
     * Initializes the connection pool with minPoolSize connections.
     */
    public synchronized void initializePool() throws SQLException {
        if (initialized || isClosed) {
            return;
        }
        // In OSGi, DriverManager uses the bootstrap classloader which cannot see
        // bundle-local driver classes. Register the driver explicitly.
        try {
            if (jdbcUrl.startsWith("jdbc:h2:")) {
                java.sql.Driver h2 = (java.sql.Driver) Class.forName(
                        "org.h2.Driver", true,
                        Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(h2);
            } else {
                java.sql.Driver pg = (java.sql.Driver) Class.forName(
                        "org.postgresql.Driver", true,
                        Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(pg);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not register JDBC driver explicitly; relying on DriverManager SPI: " + e.getMessage());
        }

        for (int i = 0; i < minPoolSize; i++) {
            Connection conn = createNewRawConnection();
            if (conn != null) {
                connectionPool.offer(conn);
            }
        }
        initialized = true;
    }

    private Connection createNewRawConnection() throws SQLException {
        Properties props = buildConnectionProperties();
        return DriverManager.getConnection(jdbcUrl, props);
    }

    /**
     * Obtains an active JDBC connection from the pool, establishing a new one if necessary.
     */
    public Connection getConnection() throws SQLException {
        if (isClosed) {
            throw new SQLException("Connection pool has been closed.");
        }
        if (!initialized) {
            initializePool();
        }

        try {
            Connection conn = connectionPool.poll(100, TimeUnit.MILLISECONDS);
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                return conn;
            }
            return createNewRawConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for PostgreSQL connection", e);
        }
    }

    /**
     * Returns a used connection back to the pool.
     */
    public void releaseConnection(Connection conn) {
        if (conn == null || isClosed) {
            return;
        }
        try {
            if (!conn.isClosed() && conn.isValid(2)) {
                connectionPool.offer(conn);
            } else {
                try {
                    conn.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error releasing connection to pool", e);
        }
    }

    /**
     * Checks database health by executing a probe query.
     */
    public boolean checkConnectionHealth() {
        if (jdbcUrl == null) {
            return false;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            boolean ok = rs.next() && rs.getInt(1) == 1;
            releaseConnection(conn);
            return ok;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Database health probe failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes all connections in the pool.
     */
    public synchronized void close() {
        isClosed = true;
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (Exception ignored) {}
        }
    }
}
