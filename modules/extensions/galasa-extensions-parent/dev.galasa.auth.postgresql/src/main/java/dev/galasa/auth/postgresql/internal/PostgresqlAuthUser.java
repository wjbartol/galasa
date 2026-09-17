/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.galasa.framework.spi.auth.IFrontEndClient;
import dev.galasa.framework.spi.auth.IInternalUser;
import dev.galasa.framework.spi.auth.IUser;

/**
 * PostgreSQL implementation of Galasa User SPI.
 */
public class PostgresqlAuthUser implements IUser, IInternalUser {

    private final String userNumber;
    private final String loginId;
    private String roleId;
    private String version = "1";
    private int priority = 0;
    private String dexUserId;
    private final List<IFrontEndClient> clients = new ArrayList<>();

    public PostgresqlAuthUser(String userNumber, String loginId, String clientName, String roleId) {
        this.userNumber = userNumber;
        this.loginId = loginId;
        this.roleId = roleId;
        if (clientName != null) {
            this.clients.add(new PostgresqlFrontEndClient(clientName));
        }
    }

    @Override
    public String getUserNumber() {
        return userNumber;
    }

    @Override
    public String getLoginId() {
        return loginId;
    }

    @Override
    public String getRoleId() {
        return roleId != null ? roleId : IUser.DEFAULT_ROLE_ID_WHEN_MISSING;
    }

    @Override
    public void setRoleId(String newRoleId) {
        this.roleId = newRoleId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public Collection<IFrontEndClient> getClients() {
        return clients;
    }

    @Override
    public IFrontEndClient getClient(String clientName) {
        if (clientName == null) return null;
        for (IFrontEndClient client : clients) {
            if (clientName.equals(client.getClientName())) {
                return client;
            }
        }
        return null;
    }

    @Override
    public void addClient(IFrontEndClient client) {
        if (client != null) {
            clients.add(client);
        }
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newPriority) {
        this.priority = newPriority;
    }

    @Override
    public String getDexUserId() {
        return dexUserId != null ? dexUserId : loginId;
    }

    public void setDexUserId(String dexUserId) {
        this.dexUserId = dexUserId;
    }
}
