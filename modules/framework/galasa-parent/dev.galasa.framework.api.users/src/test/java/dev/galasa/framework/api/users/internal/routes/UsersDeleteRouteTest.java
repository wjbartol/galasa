package dev.galasa.framework.api.users.internal.routes;

import java.time.Instant;
import java.util.Map;

import javax.servlet.ServletOutputStream;

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;

import dev.galasa.framework.api.common.BaseServletTest;
import dev.galasa.framework.api.common.EnvironmentVariables;
import dev.galasa.framework.api.common.HttpMethod;
import dev.galasa.framework.api.common.InternalUser;
import dev.galasa.framework.api.common.mocks.MockAuthStoreService;
import dev.galasa.framework.api.common.mocks.MockEnvironment;
import dev.galasa.framework.api.common.mocks.MockFramework;
import dev.galasa.framework.api.common.mocks.MockFrontEndClient;
import dev.galasa.framework.api.common.mocks.MockHttpServletRequest;
import dev.galasa.framework.api.common.mocks.MockHttpServletResponse;
import dev.galasa.framework.api.common.mocks.MockTimeService;
import dev.galasa.framework.api.common.mocks.MockUser;
import dev.galasa.framework.api.users.mocks.MockUsersServlet;
import dev.galasa.framework.spi.auth.IInternalUser;
import dev.galasa.framework.spi.utils.GalasaGson;

public class UsersDeleteRouteTest extends BaseServletTest {


    GalasaGson gson = new GalasaGson(); 
    Map<String, String> headerMap = Map.of("Authorization", "Bearer " + BaseServletTest.DUMMY_JWT);

    @Test
    public void testUsersDeleteRequestReturnsNotFoundDueToMissingUserDoc() throws Exception {
        // Given...
        MockEnvironment env = new MockEnvironment();
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService authStoreService = new MockAuthStoreService(mockTimeService);
        MockFramework framework = new MockFramework(authStoreService);

        String baseUrl = "http://my.server/api";

        String loginId = "admin";

        env.setenv(EnvironmentVariables.GALASA_USERNAME_CLAIMS, "preferred_username");
        env.setenv(EnvironmentVariables.GALASA_EXTERNAL_API_URL,baseUrl);
        MockUsersServlet servlet = new MockUsersServlet(framework, env);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("/" + loginId, headerMap);
        mockRequest.setMethod(HttpMethod.DELETE.toString());

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletOutputStream outStream = servletResponse.getOutputStream();      

        // When...
        servlet.init();
        servlet.doDelete(mockRequest, servletResponse);

        assertThat(servletResponse.getStatus()).isEqualTo(404);
        checkErrorStructure(outStream.toString(), 5083, "GAL5083E",
            "Unable to retrieve a user with the given ‘loginId’. No such user exists. Check your request query parameters and try again.");
        assertThat(servletResponse.getContentType()).isEqualTo("application/json");
    }

    @Test
    public void testUsersDeletesAUserReturnsOK() throws Exception {
        // Given...
        MockEnvironment env = new MockEnvironment();
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService authStoreService = new MockAuthStoreService(mockTimeService);
        MockFramework framework = new MockFramework(authStoreService);

        String baseUrl = "http://my.server/api";

        String loginId = "user-1";

        env.setenv(EnvironmentVariables.GALASA_USERNAME_CLAIMS, "preferred_username");
        env.setenv(EnvironmentVariables.GALASA_EXTERNAL_API_URL,baseUrl);
        MockUsersServlet servlet = new MockUsersServlet(framework, env);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("/" + loginId, headerMap);
        mockRequest.setMethod(HttpMethod.DELETE.toString());

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();     
        
        MockUser mockUser1 = createMockUser("user-1", "docid", "web-ui");
        authStoreService.addUser(mockUser1);

        // When...
        servlet.init();
        servlet.doDelete(mockRequest, servletResponse);

        assertThat(servletResponse.getStatus()).isEqualTo(204);
    }

    @Test
    public void testUsersDeletesAUserUsingMeKeywordReturnsOK() throws Exception {
        // Given...
        MockEnvironment env = new MockEnvironment();
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService authStoreService = new MockAuthStoreService(mockTimeService);
        MockFramework framework = new MockFramework(authStoreService);

        String baseUrl = "http://my.server/api";

        String loginId = "me";

        env.setenv(EnvironmentVariables.GALASA_USERNAME_CLAIMS, "preferred_username");
        env.setenv(EnvironmentVariables.GALASA_EXTERNAL_API_URL,baseUrl);
        MockUsersServlet servlet = new MockUsersServlet(framework, env);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("/" + loginId, headerMap);
        mockRequest.setMethod(HttpMethod.DELETE.toString());

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();     
        
        MockUser mockUser1 = createMockUser("testRequestor", "docid", "web-ui");
        authStoreService.addUser(mockUser1);

        // When...
        servlet.init();
        servlet.doDelete(mockRequest, servletResponse);

        assertThat(servletResponse.getStatus()).isEqualTo(204);
    }

    @Test
    public void testUsersDeletesAUserAndTheirAccessTokensReturnsOK() throws Exception {
        // Given...
        MockEnvironment env = new MockEnvironment();
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService authStoreService = new MockAuthStoreService(mockTimeService);
        MockFramework framework = new MockFramework(authStoreService);

        String baseUrl = "http://my.server/api";

        String loginId = "user-1";

        env.setenv(EnvironmentVariables.GALASA_USERNAME_CLAIMS, "preferred_username");
        env.setenv(EnvironmentVariables.GALASA_EXTERNAL_API_URL,baseUrl);
        MockUsersServlet servlet = new MockUsersServlet(framework, env);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("/" + loginId, headerMap);
        mockRequest.setMethod(HttpMethod.DELETE.toString());

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();     

        IInternalUser owner = new InternalUser("user-1", "dexId");
        authStoreService.storeToken("some-client-id", "test-token", owner);
        
        MockUser mockUser1 = createMockUser("user-1", "docid", "web-ui");
        authStoreService.addUser(mockUser1);

        // When...
        servlet.init();
        servlet.doDelete(mockRequest, servletResponse);

        assertThat(servletResponse.getStatus()).isEqualTo(204);
    }

    

    private MockUser createMockUser(String loginId, String userNumber, String clientName){

        MockFrontEndClient newClient = new MockFrontEndClient("web-ui");
        newClient.name = clientName;
        newClient.lastLoginTime = Instant.parse("2024-10-18T14:49:50.096329Z");
            

        MockUser mockUser = new MockUser();
        mockUser.userNumber = userNumber;
        mockUser.loginId = loginId;
        mockUser.addClient(newClient);

        return mockUser;

    }

}
