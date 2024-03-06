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
import java.net.URLConnection;
import java.text.MessageFormat;
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

/**
 * Merge all the test catalogs on the dependency list
 * 
 * @author Michael Baylis
 *
 */
@Mojo(name = "deploytestcat", defaultPhase = LifecyclePhase.DEPLOY, threadSafe = true)
public class DeployTestCatalog extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${galasa.test.stream}", readonly = true, required = false)
    private String       testStream;

    // To deploy the test catalog we need to authenticate using this token.
    @Parameter(defaultValue = "${galasa.token}", readonly = true , required = false)
    private String       galasaAccessToken;

    @Parameter(defaultValue = "${galasa.bootstrap}", readonly = true, required = false)
    private URL          bootstrapUrl;
    
    @Parameter(defaultValue = "${galasa.skip.bundletestcatatlog}", readonly = true, required = false)
    private boolean      typoSkip;
    
    @Parameter(defaultValue = "${galasa.skip.bundletestcatalog}", readonly = true, required = false)
    private boolean      correctSkip;
    
    @Parameter(defaultValue = "${galasa.skip.deploytestcatatlog}", readonly = true, required = false)
    private boolean      typoSkipDeploy;
    
    @Parameter(defaultValue = "${galasa.skip.deploytestcatalog}", readonly = true, required = false)
    private boolean      correctSkipDeploy;
    
    private boolean      skip = setCorrectBooleanValue(correctSkip, typoSkip);
    private boolean      skipDeploy = setCorrectBooleanValue(correctSkipDeploy, typoSkipDeploy);

    /**
     * In order to slowly deprecate the plugin with the wrong plugin spelling of 'catatlog',
     * this function checks if either variation (the typo or correct spelling) of the plugin has been set to true,
     * and returns true, otherwise false
     * 
     * @param correctSkip The correct spelling 'catalog'
     * @param typoSkip    The wrong spelling 'catatlog'
     * @return skip       The correct boolean value depending on the plugin variable values
     */
    protected static boolean setCorrectBooleanValue(boolean correctSkip, boolean typoSkip) {
        boolean skip = false;
        //boolean default value is false
        if (correctSkip || typoSkip) {
            //if one of the skip variables is set, we know for sure to skip
            skip = true;
        } 
        return skip;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("DeployTestCatalog - execute()");

        if (skip || skipDeploy) {
            getLog().info("Skipping Deploy Test Catalog");
            return;
        }

        if (testStream == null) {
            getLog().info("Skipping Deploy Test Catalog - test stream name is missing");
            return;
        }

        if (bootstrapUrl == null) {
            getLog().info("Skipping Deploy Test Catalog - Bootstrap URL is missing");
            return;
        }

        if (!"galasa-obr".equals(project.getPackaging())) {
            getLog().info("Skipping Bundle Test Catalog deploy, not a galasa-obr project");
            return;
        }

        Artifact testCatalogArtifact = getTestCatalogArtifact();

        if (testCatalogArtifact == null) {
            getLog().info("Skipping Bundle Test Catalog deploy, no test catalog artifact present");
            return;
        }

        Properties bootstrapProperties = getBootstrapProperties();

        URL testcatalogUrl = calculateTestCatalogUrl(bootstrapProperties);

        checkGalasaAccessTokenIsValid(this.galasaAccessToken); 

        String jwt = getAuthenticatedJwt(this.galasaAccessToken, this.bootstrapUrl) ;

        publishTestCatalogToGalasaServer(testcatalogUrl,jwt, testCatalogArtifact);
    }


    private void checkGalasaAccessTokenIsValid(String galasaAccessToken) throws MojoExecutionException {
        if (galasaAccessToken==null || galasaAccessToken.isEmpty()) {
            throw new MojoExecutionException("No Galasa authentication token supplied. Use the galasa-token variable.");
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
            throw new MojoExecutionException("Problem publishing the test catalog. Could not open URL connection to the Galasa server.",ioEx);
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
            throw new MojoExecutionException("Problem publishing the test catalog. Problem dealing with response from Galasa server.",ioEx);
        } 

        if (rc != HttpURLConnection.HTTP_OK) {
            getLog().error("Deploy to Test Catalog Store failed:-");
            getLog().error(Integer.toString(rc) + " - " + message);
            if (!response.isEmpty()) {
                getLog().error(response);
            }
        }
    }

    private URL calculateTestCatalogUrl(Properties bootstrapProperties) throws MojoExecutionException {
        String sTestcatalogUrl = bootstrapProperties.getProperty("framework.testcatalog.url");
        if (sTestcatalogUrl == null || sTestcatalogUrl.trim().isEmpty()) {
            String sBootstrapUrl = bootstrapUrl.toString();
            if (!sBootstrapUrl.endsWith("/bootstrap")) {
                throw new MojoExecutionException(
                        "Unable to calculate the test catalog url, the bootstrap url does not end with /bootstrap, need a framework.testcatalog.url property in the bootstrap");
            }

            sTestcatalogUrl = sBootstrapUrl.substring(0, sBootstrapUrl.length() - 10) + "/testcatalog";
        }

        // convert into a proper URL
        URL testCatalogUrl ;
        try {
            testCatalogUrl = new URL(sTestcatalogUrl + "/" + testStream);
        } catch(Exception ex) {
            throw new MojoExecutionException("Problem publishing the test catalog. Badly formed URL to the Galasa server.",ex);
        }
        return testCatalogUrl;
    }

    private Properties getBootstrapProperties() throws MojoExecutionException {
        Properties bootstrapProperties = new Properties();
        try {
            URLConnection connection = bootstrapUrl.openConnection();
            String msg = MessageFormat.format("execute(): URLConnection: connected to:{0}",connection.getURL().toString());
            getLog().info(msg);
            bootstrapProperties.load(connection.getInputStream());
            getLog().info("execute(): bootstrapProperties loaded: " + bootstrapProperties);
        } catch (Exception e) {
            String errMsg = MessageFormat.format("execute() - Unable to load bootstrap properties, Reason: {0}", e);
            getLog().info(errMsg);
            throw new MojoExecutionException(errMsg, e);
        }
        return bootstrapProperties;
    }

    /**
     * Swap the galasa token for a JWT which we can use to talk to the API server on the ecosystem.
     * @return A JWT string (Java Web Token).
     * @throws MojoExecutionException
     */
    private String getAuthenticatedJwt(String galasaAccessToken, URL bootstrapUrl) throws MojoExecutionException {
        String jwt;
        try {
            String apiServerUrlString = bootstrapUrl.toString().replaceAll("/bootstrap","");
            HttpClient httpClient = HttpClientBuilder.create().build();
            URL apiServerUrl = new URL(apiServerUrlString);
            AuthenticationService authTokenService = new AuthenticationService(apiServerUrl,galasaAccessToken,httpClient);
            getLog().info("Turning the galasa access token into a jwt");
            jwt = authTokenService.getJWT();
            getLog().info("Java Web Token (jwt) obtained from the galasa ecosystem OK.");
        } catch( Exception ex) {
            throw new MojoExecutionException(ex.getMessage(),ex);
        } 
        return jwt;
    }
}
