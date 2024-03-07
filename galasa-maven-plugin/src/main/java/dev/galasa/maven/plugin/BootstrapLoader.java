/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.maven.plugin;

import java.net.URL;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public interface BootstrapLoader {
    public Properties getBootstrapProperties(URL bootstrapUrl, Log log ) throws MojoExecutionException ;
}
