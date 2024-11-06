/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.api.users.mocks;

import dev.galasa.framework.api.common.ResponseBuilder;
import dev.galasa.framework.api.common.mocks.MockEnvironment;
import dev.galasa.framework.api.users.UsersServlet;
import dev.galasa.framework.auth.spi.IAuthService;
import dev.galasa.framework.auth.spi.mocks.MockAuthServiceFactory;

public class MockUsersServlet extends UsersServlet{

	public MockUsersServlet(IAuthService authService, MockEnvironment env) {
        this.env = env;

        setAuthServiceFactory(new MockAuthServiceFactory(authService));
        setResponseBuilder(new ResponseBuilder(env));
    }
}
