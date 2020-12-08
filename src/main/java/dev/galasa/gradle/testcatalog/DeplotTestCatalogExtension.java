package dev.galasa.gradle.testcatalog;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;

public class DeplotTestCatalogExtension {
    
    @Inject
    public DeplotTestCatalogExtension(ObjectFactory objectFactory) {
    }

    public String bootstrap;
    public String stream;
    
    public void setStream(String stream) {
        this.stream = stream;
    }
    
    public void setBootstrap(String bootstrap) {
        this.bootstrap = bootstrap;
    }
    
}
