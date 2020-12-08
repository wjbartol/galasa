/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.gradle.testcatalog;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;

public class TestCatalogDeployTask extends DefaultTask {

    @TaskAction
    public void genobr() throws Exception {
        getLogger().debug("Deploying Testcatalog");
        System.out.println("here");
        
        DeplotTestCatalogExtension extension = getProject().getExtensions().findByType(DeplotTestCatalogExtension.class);
        System.out.println(extension.bootstrap);
    }



    public void apply() {
        Task gentestcat = getProject().getTasks().getByName("gentestcat");
        
        getDependsOn().add(gentestcat);
        
        getInputs().files(gentestcat.getOutputs().getFiles());
        
    }

}
