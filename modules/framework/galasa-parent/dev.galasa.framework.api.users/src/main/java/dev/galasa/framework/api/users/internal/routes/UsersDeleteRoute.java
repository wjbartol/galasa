package dev.galasa.framework.api.users.internal.routes;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static dev.galasa.framework.api.common.ServletErrorMessage.*;

import dev.galasa.framework.spi.auth.IInternalAuthToken;

import dev.galasa.framework.api.common.BaseRoute;
import dev.galasa.framework.api.common.Environment;
import dev.galasa.framework.api.common.EnvironmentVariables;
import dev.galasa.framework.api.common.InternalServletException;
import dev.galasa.framework.api.common.JwtWrapper;
import dev.galasa.framework.api.common.QueryParameters;
import dev.galasa.framework.api.common.ResponseBuilder;
import dev.galasa.framework.api.common.ServletError;
import dev.galasa.framework.api.users.UsersServlet;
import dev.galasa.framework.spi.FrameworkException;
import dev.galasa.framework.spi.auth.AuthStoreException;
import dev.galasa.framework.spi.auth.IAuthStoreService;
import dev.galasa.framework.spi.auth.IUser;

public class UsersDeleteRoute extends BaseRoute{

    // Regex to match endpoint /users/{someLoginId}
    private static final String path = "\\/([a-zA-Z0-9\\-\\_]+)\\/?";

    private Environment env;
    private IAuthStoreService authStoreService;
    private BeanTransformer beanTransformer;
    private Pattern pathPattern;

    public UsersDeleteRoute(ResponseBuilder responseBuilder, Environment env,
            IAuthStoreService authStoreService) {
        super(responseBuilder, path);
        this.env = env;
        this.authStoreService = authStoreService;

        String baseServletUrl = env.getenv(EnvironmentVariables.GALASA_EXTERNAL_API_URL);

        this.beanTransformer = new BeanTransformer(baseServletUrl);
        this.pathPattern = Pattern.compile(path);
    }

    @Override
    public HttpServletResponse handleDeleteRequest(
        String pathInfo,
        QueryParameters queryParams,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws FrameworkException{

        logger.info("handleDeleteRequest() entered");

        String loginId = validateAndFetchLoginId(request, pathInfo);
        deleteUser(loginId);

        logger.info("handleDeleteRequest() exiting");
        return getResponseBuilder().buildResponse(request, response, HttpServletResponse.SC_NO_CONTENT);
    }

    private String validateAndFetchLoginId(HttpServletRequest request, String pathInfo) throws InternalServletException{

        JwtWrapper jwtWrapper = new JwtWrapper(request, env);

        try{

            Matcher matcher = pathPattern.matcher(pathInfo);
            matcher.matches();

            String loginId = matcher.group(1);
            
            // Checking for 'me' keyword if someone wants to delete themselves from an ecosystem
            // To be made admin only operation once we have RBAC ready
            if(loginId.equals(UsersServlet.QUERY_PARAMETER_LOGIN_ID_VALUE_MYSELF)){
                loginId = jwtWrapper.getUsername();
            }

            return loginId;

        }catch(Exception ex){
            ServletError error = new ServletError(GAL5085_FAILED_TO_GET_LOGIN_ID_FROM_URL);
            throw new InternalServletException(error, HttpServletResponse.SC_NOT_FOUND, ex);
        }

    }

    private void deleteUser(String loginId) throws AuthStoreException, InternalServletException{

        //Need to delete access tokens of a user if we delete the user
        List<IInternalAuthToken> tokens = authStoreService.getTokensByLoginId(loginId);
        for (IInternalAuthToken token : tokens) {
            authStoreService.deleteToken(token.getTokenId());
        }

        try{

            IUser userToBeDeleted = authStoreService.getUserByLoginId(loginId);

            if(userToBeDeleted == null){
                ServletError error = new ServletError(GAL5083_ERROR_USER_NOT_FOUND);
                throw new InternalServletException(error, HttpServletResponse.SC_NOT_FOUND);
            }

            logger.info("A user with the given loginId was found OK");
            authStoreService.deleteUser(userToBeDeleted);
            logger.info("The user with the given loginId was deleted OK");

        }catch (AuthStoreException e) {
            ServletError error = new ServletError(GAL5084_FAILED_TO_DELETE_USER);
            throw new InternalServletException(error, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }
}
