/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql;

import java.net.URI;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.osgi.service.component.annotations.Component;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.IFrameworkInitialisation;
import dev.galasa.framework.spi.IResultArchiveStoreRegistration;
import dev.galasa.framework.spi.ResultArchiveStoreException;

/**
 * OSGi Declarative Services registration component that plugs the PostgreSQL
 * RAS into the Galasa framework's provider discovery mechanism.
 *
 * <p>The framework calls every registered {@link IResultArchiveStoreRegistration}
 * at startup. This class checks each configured RAS URI for the {@code postgresql:}
 * scheme; registers the first match as a {@link PostgresqlRasStore}. If no
 * {@code postgresql:} URI is found, returns silently.
 *
 * <p>Expected bootstrap property:
 * <pre>
 *   framework.result.archive.store=postgresql:jdbc:postgresql://galasa-postgresql:5432/galasa
 * </pre>
 */
@Component(service = { IResultArchiveStoreRegistration.class })
public class PostgresqlRasRegistration implements IResultArchiveStoreRegistration {

    /** URI scheme that identifies this provider. */
    public static final String URL_SCHEME = "postgresql";

    @Override
    public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
            throws ResultArchiveStoreException {

        List<URI> rasUris = frameworkInitialisation.getResultArchiveStoreUris();
        for (URI uri : rasUris) {
            if (URL_SCHEME.equals(uri.getScheme())) {
                String jdbcUrl = uri.toString().substring((URL_SCHEME + ":").length());
                try {
                    PostgresqlConnectionPoolManager pool = PostgresqlConnectionPoolManager.fromJdbcUrl(jdbcUrl);
                    frameworkInitialisation.registerResultArchiveStoreService(new PostgresqlRasStore(pool));
                } catch (Exception e) {
                    throw new ResultArchiveStoreException(
                            "Failed to initialise PostgreSQL RAS Store: " + e.getMessage(), e);
                }
                return; // Only one PostgreSQL RAS instance supported
            }
        }
    }
}
