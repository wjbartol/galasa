package dev.galasa.framework.auth.spi.mocks;

import javax.servlet.ServletException;

import dev.galasa.framework.auth.spi.IAuthService;
import dev.galasa.framework.auth.spi.IAuthServiceFactory;

public class MockAuthServiceFactory implements IAuthServiceFactory {

    private IAuthService authService;

    public MockAuthServiceFactory(IAuthService authService) {
        this.authService = authService;
    }

    @Override
    public IAuthService getAuthService() throws ServletException {
        return authService;
    }
    
}
