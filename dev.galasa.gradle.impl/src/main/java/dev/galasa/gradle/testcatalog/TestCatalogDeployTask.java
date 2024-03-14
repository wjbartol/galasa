/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.gradle.testcatalog;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import dev.galasa.plugin.common.*;

import dev.galasa.plugin.common.impl.*;

public class TestCatalogDeployTask extends DefaultTask {

    @TaskAction
    public void genobr() throws Exception {
        getLogger().debug("Deploying Testcatalog");
        DeployTestCatalogExtension extension = getProject().getExtensions().findByType(DeployTestCatalogExtension.class);
                
        if (extension== null || extension.bootstrap == null || extension.bootstrap.trim().isEmpty()) {
            throw new TestCatalogException("The bootstrap url is missing in the deployTestCatalog extension");
        }
        
        URL bootstrapUrl = null;
        try {
            bootstrapUrl = new URL(extension.bootstrap.trim());
        } catch(Exception e) {
            throw new TestCatalogException("Invalid bootstrap url '" + extension.bootstrap + "' in the deployTestCatalog extension",e);
        }
        
        if (extension.stream == null || extension.stream.trim().isEmpty()) {
            throw new TestCatalogException("The test stream is missing in the deployTestCatalog extension");
        }
        
        String testStream = extension.stream.trim().toLowerCase();

        Path pathArtifact = Paths.get(getInputs().getFiles().getSingleFile().toURI());

        String galasaAccessToken = extension.getGalasaToken();

        // Instantiate all the classes to isolave gradle dependencies...
        WrappedLog wrappedLog = new WrappedLogGradle(getLogger());
        PluginCommonFactory<TestCatalogException> factory = new PluginCommonFactoryImpl<TestCatalogException>();
        ErrorRaiser<TestCatalogException> errorRaiser = new ErrorRaiserGradleImpl(getLogger());
        TestCatalogArtifact<TestCatalogException> wrappedTestCatalogArtifact = new TestCatalogArtifactGradleImpl(pathArtifact, errorRaiser);
        TestCatalogArtifactDeployer<TestCatalogException> deployer = new TestCatalogArtifactDeployer<TestCatalogException>(
            wrappedLog, errorRaiser, factory);

        // Deploy the test catalog to the Galasa server...
        deployer.deployToServer(bootstrapUrl, testStream, galasaAccessToken, wrappedTestCatalogArtifact);
    }

    public void apply() {
        Task gentestcat = getProject().getTasks().getByName("mergetestcat");
        
        getDependsOn().add(gentestcat);
        
        getInputs().files(gentestcat.getOutputs().getFiles());
    }

}
