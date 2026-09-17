/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.dss.postgresql.internal;

import java.net.URI;

import javax.validation.constraints.NotNull;

import org.osgi.service.component.annotations.Component;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.DynamicStatusStoreException;
import dev.galasa.framework.spi.IDynamicStatusStoreRegistration;
import dev.galasa.framework.spi.IFrameworkInitialisation;

/**
 * OSGi Declarative Services registration component that plugs the PostgreSQL
 * DSS into the Galasa framework's provider discovery mechanism.
 *
 * <p>The framework calls every registered {@link IDynamicStatusStoreRegistration}
 * at startup. This class checks the DSS URI for the {@code postgresql:} scheme;
 * if matched, constructs and registers a {@link PostgresqlDynamicStatusStore}.
 *
 * <p>Expected bootstrap property:
 * <pre>
 *   framework.dynamicstatus.store=postgresql:jdbc:postgresql://galasa-postgresql:5432/galasa
 * </pre>
 */
@Component(service = { IDynamicStatusStoreRegistration.class })
public class PostgresqlDssRegistration implements IDynamicStatusStoreRegistration {

    /** URI scheme that identifies this provider. */
    public static final String URL_SCHEME = "postgresql";

    @Override
    public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
            throws DynamicStatusStoreException {

        URI uri = frameworkInitialisation.getDynamicStatusStoreUri();
        if (uri == null || !URL_SCHEME.equals(uri.getScheme())) {
            return;
        }

        String jdbcUrl = uri.toString().substring((URL_SCHEME + ":").length());

        try {
            PostgresqlConnectionPoolManager pool = PostgresqlConnectionPoolManager.fromJdbcUrl(jdbcUrl);
            frameworkInitialisation.registerDynamicStatusStore(new PostgresqlDynamicStatusStore(pool));
        } catch (Exception e) {
            throw new DynamicStatusStoreException(
                    "Failed to initialise PostgreSQL DSS Store: " + e.getMessage(), e);
        }
    }
}
