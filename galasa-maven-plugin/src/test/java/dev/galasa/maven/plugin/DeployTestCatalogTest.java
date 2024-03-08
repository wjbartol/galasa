/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.maven.plugin;

import java.net.URL;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class DeployTestCatalogTest { 

    @Test
    public void TestCanCreateDeployTestCatalog() {
        new DeployTestCatalog();
    }

    @Test
    public void TestSkipCatalogDeployOldSpellingStillSkipsDoingWork() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        // The old spelling is detected.
        command.skipDeployTestCatalogOldSpelling = true;

        command.execute();

        String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipCatalogDeployNewSpellingStillSkipsDoingWork() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        // The old spelling is detected.
        command.skipDeployTestCatalog = true;

        command.execute();

        String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipCatalogDeployNewAndOldSpellingStillSkipsDoingWork() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        // The old spelling is detected.
        command.skipDeployTestCatalog = true;
        command.skipDeployTestCatalogOldSpelling = true;

        command.execute();

        String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipBundleCatalogNewSpellingStillSkipsDoingWork() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        // The old spelling is detected.
        command.skipBundleTestCatalog = true;
        
        command.execute();

        String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipBundleCatalogOldpellingStillSkipsDoingWork() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        // The old spelling is detected.
        command.skipBundleTestCatalogOldSpelling = true;
        
        command.execute();

        String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipsIfNoArtifactPresent() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("galasa-obr");
        command.project = project;

        command.execute();

        String expectedLogRecord = "WARN:Skipping Bundle Test Catalog deploy, no test catalog artifact present";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipsWorkIfProjectNotAnOBR() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        project.setPackaging("not-a-galasa-obr");
        command.project = project;


        command.execute();
        
        String expectedLogRecord = "INFO:Skipping Bundle Test Catalog deploy, not a galasa-obr project";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipsIfStreamNotSpecified() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        // command.testStream = "myTestStream";

        MavenProject project = new MavenProject();
        command.project = project;

        command.execute();

        String expectedLogRecord = "WARN:Skipping Deploy Test Catalog - test stream name is missing";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipsIfBootstrapNotProvided() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        // command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        command.project = project;

        command.execute();

        String expectedLogRecord = "WARN:Skipping Deploy Test Catalog - Bootstrap URL is missing";
        mockLog.assertContainsRecord(expectedLogRecord);
    }

    @Test
    public void TestSkipsNonObrProjects() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        MockLog mockLog = new MockLog();
        command.setLog(mockLog);

        command.testStream = "myTestStream";
        command.bootstrapUrl = new URL("http://myBootstrapUrl");

        MavenProject project = new MavenProject();
        command.project = project;

        command.execute();

        String expectedLogRecord = "INFO:Skipping Bundle Test Catalog deploy, not a galasa-obr project";
        mockLog.assertContainsRecord(expectedLogRecord);
    }



    @Test
    public void TestCalculateTestCatalogUrlUsesBootstrapPropertyIfSet() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        props.setProperty("framework.testcatalog.url", "https://my.explicitly.set.bootstrap.url");
        URL calculatedUrl = command.calculateTestCatalogUrl(props, "myTestStream", new URL("https://my.bootstrap.url"));
        assertThat(calculatedUrl.toString()).isEqualTo("https://my.explicitly.set.bootstrap.url/myTestStream");
    }

    @Test
    public void TestCalculateTestCatalogUrlUsesBootstrapUrlByDefault() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        // props.setProperty("framework.testcatalog.url", "https://my.explicitly.set.bootstrap.url");
        URL calculatedUrl = command.calculateTestCatalogUrl(props, "myTestStream", new URL("https://my.bootstrap.url/bootstrap"));
        assertThat(calculatedUrl.toString()).isEqualTo("https://my.bootstrap.url/testcatalog/myTestStream");
    }

    @Test
    public void TestCalculateTestCatalogUrlSpotsBootstrapUrlWhichDoesntEndInBootstrap() throws Exception {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        // props.setProperty("framework.testcatalog.url", "https://my.explicitly.set.bootstrap.url");
        Exception ex = catchException( ()-> command.calculateTestCatalogUrl(props, "myTestStream", new URL("https://my.bootstrap.url/bootstrap-not-at-end")) );
        assertThat(ex).isInstanceOf(MojoExecutionException.class);
        
        assertThat(ex).hasMessageContaining("Unable to calculate the test catalog url, the bootstrap url does not end with /bootstrap, need a framework.testcatalog.url property in the bootstrap");
    }

    @Test
    public void testCalcServerUsesAuthenticationWhenPropMissingShouldBeFalse() {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        boolean isAuthEnabled = command.calculateWhetherAuthenticationIsEnabledOnServer(props);
        assertThat(isAuthEnabled).isFalse();
    }

    @Test
    public void testCalcServerUsesAuthenticationWhenPropFalseShouldBeFalse() {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        props.setProperty(DeployTestCatalog.BOOTSTRAP_PROPERTY_NAME_IS_AUTH_ENABLED,"false");
        boolean isAuthEnabled = command.calculateWhetherAuthenticationIsEnabledOnServer(props);
        assertThat(isAuthEnabled).isFalse();
    }

    @Test
    public void testCalcServerUsesAuthenticationWhenPropTRUEShouldBeTrue() {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        props.setProperty(DeployTestCatalog.BOOTSTRAP_PROPERTY_NAME_IS_AUTH_ENABLED,"TRUE");
        boolean isAuthEnabled = command.calculateWhetherAuthenticationIsEnabledOnServer(props);
        assertThat(isAuthEnabled).isTrue();
    }

    @Test
    public void testCalcServerUsesAuthenticationWhenPropTrueShouldBeTrue() {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        props.setProperty(DeployTestCatalog.BOOTSTRAP_PROPERTY_NAME_IS_AUTH_ENABLED,"True");
        boolean isAuthEnabled = command.calculateWhetherAuthenticationIsEnabledOnServer(props);
        assertThat(isAuthEnabled).isTrue();
    }

    @Test
    public void testCalcServerUsesAuthenticationWhenProptrueShouldBeTrue() {
        DeployTestCatalog command = new DeployTestCatalog();
        Properties props = new Properties();
        props.setProperty(DeployTestCatalog.BOOTSTRAP_PROPERTY_NAME_IS_AUTH_ENABLED,"true");
        boolean isAuthEnabled = command.calculateWhetherAuthenticationIsEnabledOnServer(props);
        assertThat(isAuthEnabled).isTrue();
    }


    // This is my exploration unit test.
    //
    // The unit tests are not yet complete, as they don't test the last piece where the test catalog file is
    // sent off to the URL.
    // 
    // @SuppressWarnings("deprecation")
    // @Test
    // public void TestXXX() throws Exception {
    //     DeployTestCatalog command = new DeployTestCatalog();
    //     MockLog mockLog = new MockLog();
    //     command.setLog(mockLog);

    //     command.testStream = "myTestStream";
    //     command.bootstrapUrl = new URL("http://myBootstrapUrl/bootstrap");

    //     MavenProject project = new MavenProject();
    //     project.setPackaging("galasa-obr");
    //     command.project = project;

    //     MockArtifact testCatalogArtifact = new MockArtifact();
    //     project.addAttachedArtifact(testCatalogArtifact);
    //     testCatalogArtifact.type = "json";
    //     testCatalogArtifact.classifier = "testcatalog";
        
    //     // Set a mock boostrap loader...
    //     command.bootstrapLoader = new BootstrapLoader() {
    //         @Override
    //         public Properties getBootstrapProperties(URL bootstrapUrl, Log log) throws MojoExecutionException {
    //             return new Properties();
    //         }
    //     };

    //     command.galasaAccessToken="my:token";

    //     command.authFactory = new AuthenticationServiceFactory() {
    //         @Override
    //         public AuthenticationService newAuthenticationService(URL apiServerUrl, String galasaAccessToken,
    //                 HttpClient httpClient) throws AuthenticationException {
    //             return new AuthenticationService() {

    //                 @Override
    //                 public String getJWT() throws AuthenticationException {
    //                    return "myJWT";
    //                 }
    //             };
    //         }
    //     };

    //     command.execute();

    //     String expectedLogRecord = "INFO:Skipping Deploy Test Catalog - because the property galasa.skip.deploytestcatalog or galasa.skip.bundletestcatalog is set";
    //     mockLog.assertContainsRecord(expectedLogRecord);
    // }
}
