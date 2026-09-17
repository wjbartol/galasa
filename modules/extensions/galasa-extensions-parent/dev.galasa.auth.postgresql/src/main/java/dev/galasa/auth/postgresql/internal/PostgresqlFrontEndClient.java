/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import java.time.Instant;
import dev.galasa.framework.spi.auth.IFrontEndClient;

/**
 * PostgreSQL implementation of Galasa FrontEndClient SPI.
 */
public class PostgresqlFrontEndClient implements IFrontEndClient {
    private final String clientName;
    private Instant lastLogin;

    public PostgresqlFrontEndClient(String clientName) {
        this(clientName, Instant.now());
    }

    public PostgresqlFrontEndClient(String clientName, Instant lastLogin) {
        this.clientName = clientName;
        this.lastLogin = lastLogin;
    }

    @Override
    public String getClientName() {
        return clientName;
    }

    @Override
    public Instant getLastLogin() {
        return lastLogin;
    }

    @Override
    public void setLastLogin(Instant lastLoginTime) {
        this.lastLogin = lastLoginTime;
    }
}
