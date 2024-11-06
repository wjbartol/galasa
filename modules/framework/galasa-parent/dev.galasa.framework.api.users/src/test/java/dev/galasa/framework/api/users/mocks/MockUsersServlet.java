/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.api.users.mocks;

import dev.galasa.framework.api.common.ResponseBuilder;
import dev.galasa.framework.api.common.mocks.MockEnvironment;
import dev.galasa.framework.api.users.UsersServlet;
import dev.galasa.framework.auth.spi.AuthServiceFactory;
import dev.galasa.framework.auth.spi.IAuthService;

public class MockUsersServlet extends UsersServlet{

	public MockUsersServlet(IAuthService authService, MockEnvironment env) {
        this.env = env;

        AuthServiceFactory.setAuthService(authService);
        setResponseBuilder(new ResponseBuilder(env));
    }
}
