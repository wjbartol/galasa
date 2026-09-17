/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.cps.postgresql.internal;

import java.net.URI;

import javax.validation.constraints.NotNull;

import org.osgi.service.component.annotations.Component;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IConfigurationPropertyStoreRegistration;
import dev.galasa.framework.spi.IFrameworkInitialisation;

/**
 * OSGi Declarative Services registration component that plugs the PostgreSQL
 * CPS into the Galasa framework's provider discovery mechanism.
 *
 * <p>The framework calls every registered {@link IConfigurationPropertyStoreRegistration}
 * at startup. This class checks the bootstrap CPS URI for the {@code postgresql:}
 * scheme; if matched, constructs and registers a {@link PostgresqlConfigurationPropertyStore}.
 *
 * <p>Expected bootstrap property:
 * <pre>
 *   framework.config.store=postgresql:jdbc:postgresql://galasa-postgresql:5432/galasa
 * </pre>
 */
@Component(service = { IConfigurationPropertyStoreRegistration.class })
public class PostgresqlCpsRegistration implements IConfigurationPropertyStoreRegistration {

    /** URI scheme that identifies this provider. */
    public static final String URL_SCHEME = "postgresql";

    @Override
    public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
            throws ConfigurationPropertyStoreException {

        URI uri = frameworkInitialisation.getBootstrapConfigurationPropertyStore();
        if (uri == null || !URL_SCHEME.equals(uri.getScheme())) {
            return;
        }

        String jdbcUrl = uri.toString().substring((URL_SCHEME + ":").length());

        try {
            PostgresqlConnectionPoolManager pool = PostgresqlConnectionPoolManager.fromJdbcUrl(jdbcUrl);
            frameworkInitialisation.registerConfigurationPropertyStore(
                    new PostgresqlConfigurationPropertyStore(pool));
        } catch (Exception e) {
            throw new ConfigurationPropertyStoreException(
                    "Failed to initialise PostgreSQL CPS Store: " + e.getMessage(), e);
        }
    }
}
