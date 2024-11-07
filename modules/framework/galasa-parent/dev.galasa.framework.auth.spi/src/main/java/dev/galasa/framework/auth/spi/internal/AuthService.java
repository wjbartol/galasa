/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.auth.spi.internal;

import static dev.galasa.framework.api.common.ServletErrorMessage.GAL5064_FAILED_TO_REVOKE_TOKEN;
import static dev.galasa.framework.api.common.ServletErrorMessage.GAL5066_ERROR_NO_SUCH_TOKEN_EXISTS;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dev.galasa.framework.api.common.InternalServletException;
import dev.galasa.framework.api.common.ServletError;
import dev.galasa.framework.auth.spi.IAuthService;
import dev.galasa.framework.auth.spi.IDexGrpcClient;
import dev.galasa.framework.spi.auth.AuthStoreException;
import dev.galasa.framework.spi.auth.IAuthStoreService;
import dev.galasa.framework.spi.auth.IInternalAuthToken;
import dev.galasa.framework.spi.auth.IInternalUser;

public class AuthService implements IAuthService {

    private IAuthStoreService authStoreService;
    private IDexGrpcClient dexGrpcClient;

    private final Log logger = LogFactory.getLog(getClass());

    public AuthService(IAuthStoreService authStoreService, IDexGrpcClient dexGrpcClient) {
        this.authStoreService = authStoreService;
        this.dexGrpcClient = dexGrpcClient;
    }

    @Override
    public void revokeToken(String tokenId) throws InternalServletException {
        try {
            logger.info("Attempting to revoke token with ID '" + tokenId + "'");

            IInternalAuthToken tokenToRevoke = authStoreService.getToken(tokenId);
            if (tokenToRevoke == null) {
                ServletError error = new ServletError(GAL5066_ERROR_NO_SUCH_TOKEN_EXISTS);
                throw new InternalServletException(error, HttpServletResponse.SC_NOT_FOUND);
            }

            // Delete the Dex client associated with the token
            String dexClientId = tokenToRevoke.getDexClientId();
            dexGrpcClient.deleteClient(dexClientId);

            IInternalUser tokenOwner = tokenToRevoke.getOwner();
            String dexUserId = tokenOwner.getDexUserId();
            if (dexUserId != null) {
                // Revoke the refresh token
                dexGrpcClient.revokeRefreshToken(dexUserId, dexClientId);
            }

            // Delete the token's record in the auth store
            authStoreService.deleteToken(tokenId);

            logger.info("Revoked token with ID '" + tokenId + "' OK");
        } catch (AuthStoreException ex) {
            ServletError error = new ServletError(GAL5064_FAILED_TO_REVOKE_TOKEN);
            throw new InternalServletException(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public IDexGrpcClient getDexGrpcClient() {
        return dexGrpcClient;
    }

    @Override
    public IAuthStoreService getAuthStoreService() {
        return authStoreService;
    }
}
