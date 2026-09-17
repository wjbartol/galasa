/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import dev.galasa.framework.spi.auth.IInternalAuthToken;
import dev.galasa.framework.spi.auth.IInternalUser;

/**
 * PostgreSQL implementation of Galasa Auth Token SPI.
 */
public class PostgresqlAuthToken implements IInternalAuthToken {

    private final String tokenId;
    private final String dexClientId;
    private final String description;
    private final IInternalUser owner;
    private final Instant createdAt;
    private final Instant expiresAt;

    public PostgresqlAuthToken(String tokenId, String dexClientId, String description, IInternalUser owner, int lifespanDays) {
        this.tokenId = tokenId;
        this.dexClientId = dexClientId;
        this.description = description;
        this.owner = owner;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(lifespanDays, ChronoUnit.DAYS);
    }

    @Override
    public String getTokenId() {
        return tokenId;
    }

    @Override
    public String getDexClientId() {
        return dexClientId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public IInternalUser getOwner() {
        return owner;
    }

    @Override
    public Instant getCreationTime() {
        return createdAt;
    }

    @Override
    public Instant getExpiryTime() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
