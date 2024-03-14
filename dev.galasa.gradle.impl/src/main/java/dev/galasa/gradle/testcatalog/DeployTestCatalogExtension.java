/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.gradle.testcatalog;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;

public class DeployTestCatalogExtension {
    
    @Inject
    public DeployTestCatalogExtension(ObjectFactory objectFactory) {
    }

    public String bootstrap;
    public String stream;
    public String galasaToken;
    
    public void setStream(String stream) {
        this.stream = stream;
    }
    
    public void setBootstrap(String bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void setGalasaToken(String galasaToken) {
        this.galasaToken = galasaToken;
        if (this.galasaToken!=null) {
            this.galasaToken = this.galasaToken.trim();
        }
    }

    public String getGalasaToken() {
        String token = this.galasaToken;
        return token;
    }
    
}
