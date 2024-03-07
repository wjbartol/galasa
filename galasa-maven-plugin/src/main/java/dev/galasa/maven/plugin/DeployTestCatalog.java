/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.maven.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import dev.galasa.maven.plugin.auth.AuthenticationService;
import dev.galasa.maven.plugin.auth.AuthenticationServiceFactory;
import dev.galasa.maven.plugin.auth.AuthenticationServiceFactoryImpl;

/**
 * Merge all the test catalogs on the dependency list
 */
@Mojo(name = "deploytestcat", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class DeployTestCatalog extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true)
    public MavenProject project;

    @Parameter(defaultValue = "${galasa.test.stream}", readonly = true, required = false)
    public String       testStream;

    // To deploy the test catalog we need to authenticate using this token.
    @Parameter(defaultValue = "${galasa.token}", readonly = true , required = false)
    public String       galasaAccessToken;

    @Parameter(defaultValue = "${galasa.bootstrap}", readonly = true, required = false)
    public URL          bootstrapUrl;
    
    // This spelling of the property is old/wrong/deprecated.
    @Parameter(defaultValue = "${galasa.skip.bundletestcatatlog}", readonly = true, required = false)
    public boolean      skipBundleTestCatalogOldSpelling;
    
    @Parameter(defaultValue = "${galasa.skip.bundletestcatalog}", name="skip" , readonly = true, required = false)
    public boolean      skipBundleTestCatalog;
    
    // This spelling of the property is old/wrong/deprecated.
    @Parameter(defaultValue = "${galasa.skip.deploytestcatatlog}" , readonly = true, required = false)
    public boolean      skipDeployTestCatalogOldSpelling;
    
    @Parameter(defaultValue = "${galasa.skip.deploytestcatalog}", name="skipDeploy" , readonly = true, required = false)
    public boolean      skipDeployTestCatalog;

    // A protected variable so we can inject a mock factory if needed during unit testing.
    protected AuthenticationServiceFactory authFactory = new AuthenticationServiceFactoryImpl();

    protected BootstrapLoader bootstrapLoader = new BootstrapLoaderImpl();

    public void execute() throws MojoExecutionException, MojoFailureException {

        boolean skip = (skipBundleTestCatalog || skipBundleTestCatalogOldSpelling);
        boolean skipDeploy = (skipDeployTestCatalog || skipDeployTestCatalogOldSpelling);

        getLog().info("DeployTestCatalog - execute()");
        if (skip || skipDeploy) {
            getLog().info("Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set");
            return;
        }

        if (testStream == null) {
            getLog().warn("Skipping Deploy Test Catalog - test stream name is missing");
            return;
        }

        if (bootstrapUrl == null) {
            getLog().warn("Skipping Deploy Test Catalog - Bootstrap URL is missing");
            return;
        }

        if (!"galasa-obr".equals(project.getPackaging())) {
            getLog().info("Skipping Bundle Test Catalog deploy, not a galasa-obr project");
            return;
        }

        Artifact testCatalogArtifact = getTestCatalogArtifact();

        if (testCatalogArtifact == null) {
            getLog().warn("Skipping Bundle Test Catalog deploy, no test catalog artifact present");
            return;
        }

        Properties bootstrapProperties = bootstrapLoader.getBootstrapProperties(bootstrapUrl,getLog());

        URL testcatalogUrl = calculateTestCatalogUrl(bootstrapProperties, this.testStream, this.bootstrapUrl);

        checkGalasaAccessTokenIsValid(this.galasaAccessToken); 

        String jwt = getAuthenticatedJwt(this.authFactory, this.galasaAccessToken, this.bootstrapUrl) ;

        publishTestCatalogToGalasaServer(testcatalogUrl,jwt, testCatalogArtifact);
    }

    private void checkGalasaAccessTokenIsValid(String galasaAccessToken) throws MojoExecutionException {
        if (galasaAccessToken==null || galasaAccessToken.isEmpty()) {
            String msg = "No Galasa authentication token supplied. Set the galasa.token property."+
            " The token is required to communicate with the Galasa server."+
            " Obtain a personal access token using the Galasa web user interface for the ecosystem you are trying to publish the test catalog to.";
            getLog().error(msg);
            throw new MojoExecutionException(msg);
        }
    }

    private Artifact getTestCatalogArtifact() {
        Artifact artifact = null;
        for (Artifact a : project.getAttachedArtifacts()) {
            if ("testcatalog".equals(a.getClassifier()) && "json".equals(a.getType())) {
                artifact = a;
                break;
            }
        }
        return artifact;
    }

    private void publishTestCatalogToGalasaServer(URL testCatalogUrl, String jwt, Artifact testCatalogArtifact) throws MojoExecutionException {
 
        HttpURLConnection conn = null ;
        try {
            conn = (HttpURLConnection) testCatalogUrl.openConnection();
        } catch (IOException ioEx) {
            String msg = "Problem publishing the test catalog. Could not open URL connection to the Galasa server.";
            getLog().error(msg,ioEx);
            throw new MojoExecutionException(msg,ioEx);
        }

        if (conn==null) {
            throw new MojoExecutionException("Deploy to Test Catalog Store failed. Could not open a URL connection to the Galasa server.");
        } else {
            try {
                postTestCatalogToGalasaServer(conn, testCatalogUrl, jwt, testCatalogArtifact);
            } finally {
                conn.disconnect();
            }
            getLog().info("Test Catalog successfully deployed to " + testCatalogUrl.toString());
        }
    }

    private void postTestCatalogToGalasaServer(HttpURLConnection conn, URL testCatalogUrl, String jwt, Artifact testCatalogArtifact) throws MojoExecutionException {

        int rc = 0 ;
        String response = "";
        String message = "";
        
        try {
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("PUT");
            conn.addRequestProperty("Content-Type", "application/json");
            conn.addRequestProperty("Accept", "application/json");

            conn.addRequestProperty("Authorization", "Bearer "+jwt);
            conn.addRequestProperty("ClientApiVersion","0.32.0"); // The version of the API we coded this against.

            FileUtils.copyFile(testCatalogArtifact.getFile(), conn.getOutputStream());
            rc = conn.getResponseCode();
            message = conn.getResponseMessage();

            InputStream is = null;
            if (rc != HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            if (is != null) {
                response = IOUtils.toString(is, "utf-8");
            }
        } catch(IOException ioEx) {
            String msg = "Problem publishing the test catalog. Problem dealing with response from Galasa server.";
            getLog().error(msg, ioEx);
            throw new MojoExecutionException(msg,ioEx);
        } 

        if (rc != HttpURLConnection.HTTP_OK) {
            getLog().error("Deploy to Test Catalog Store failed:-");
            getLog().error(Integer.toString(rc) + " - " + message);
            if (!response.isEmpty()) {
                getLog().error(response);
                String msg = "Failed to deploy the test catalog. The server did not reply with OK (200)";
                getLog().error(msg);
                throw new MojoExecutionException(msg);
            }
        }
    }

    // Protected so we can easily unit test it without mocking framework.
    protected URL calculateTestCatalogUrl(Properties bootstrapProperties, String testStream, URL bootstrapUrl) throws MojoExecutionException {
        String sTestcatalogUrl = bootstrapProperties.getProperty("framework.testcatalog.url");
        if (sTestcatalogUrl == null || sTestcatalogUrl.trim().isEmpty()) {
            String sBootstrapUrl = bootstrapUrl.toString();
            if (!sBootstrapUrl.endsWith("/bootstrap")) {
                String msg = "Unable to calculate the test catalog url, the bootstrap url does not end with /bootstrap, need a framework.testcatalog.url property in the bootstrap";
                getLog().error(msg);
                throw new MojoExecutionException(msg);
            }

            sTestcatalogUrl = sBootstrapUrl.substring(0, sBootstrapUrl.length() - 10) + "/testcatalog";
        }

        // convert into a proper URL
        URL testCatalogUrl ;
        try {
            testCatalogUrl = new URL(sTestcatalogUrl + "/" + testStream);
        } catch(Exception ex) {
            String msg = "Problem publishing the test catalog. Badly formed URL to the Galasa server.";
            getLog().error(msg,ex);
            throw new MojoExecutionException(msg,ex);
        }
        return testCatalogUrl;
    }

    /**
     * Swap the galasa token for a JWT which we can use to talk to the API server on the ecosystem.
     * @return A JWT string (Java Web Token).
     * @throws MojoExecutionException
     */
    private String getAuthenticatedJwt(AuthenticationServiceFactory authFactory, String galasaAccessToken, URL bootstrapUrl) throws MojoExecutionException {
        String jwt;
        try {
            String apiServerUrlString = bootstrapUrl.toString().replaceAll("/bootstrap","");
            HttpClient httpClient = HttpClientBuilder.create().build();
            URL apiServerUrl = new URL(apiServerUrlString);
            AuthenticationService authTokenService = authFactory.newAuthenticationService(apiServerUrl,galasaAccessToken,httpClient);
            getLog().info("Turning the galasa access token into a jwt");
            jwt = authTokenService.getJWT();
            getLog().info("Java Web Token (jwt) obtained from the galasa ecosystem OK.");
        } catch( Exception ex) {
            getLog().error(ex.getMessage());
            throw new MojoExecutionException(ex.getMessage(),ex);
        } 
        return jwt;
    }
}
