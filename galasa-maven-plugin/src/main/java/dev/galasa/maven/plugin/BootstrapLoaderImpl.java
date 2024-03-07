/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.maven.plugin;

import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Properties;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.MojoExecutionException;

public class BootstrapLoaderImpl implements BootstrapLoader {

    @Override
    public Properties getBootstrapProperties(URL bootstrapUrl, Log log ) throws MojoExecutionException {
        Properties bootstrapProperties = new Properties();
        try {
            URLConnection connection = bootstrapUrl.openConnection();
            String msg = MessageFormat.format("execute(): URLConnection: connected to:{0}",connection.getURL().toString());
            log.info(msg);
            bootstrapProperties.load(connection.getInputStream());
            log.info("execute(): bootstrapProperties loaded: " + bootstrapProperties);
        } catch (Exception e) {
            String errMsg = MessageFormat.format("execute() - Unable to load bootstrap properties, Reason: {0}", e);
            log.error(errMsg);
            throw new MojoExecutionException(errMsg, e);
        }
        return bootstrapProperties;
    }
}
