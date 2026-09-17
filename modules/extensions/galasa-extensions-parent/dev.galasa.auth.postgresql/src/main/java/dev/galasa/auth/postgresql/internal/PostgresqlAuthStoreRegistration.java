/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.auth.postgresql.internal;

import java.net.URI;

import javax.validation.constraints.NotNull;

import org.osgi.service.component.annotations.Component;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.IFrameworkInitialisation;
import dev.galasa.framework.spi.auth.AuthStoreException;
import dev.galasa.framework.spi.auth.IAuthStoreRegistration;

/**
 * OSGi Declarative Services registration component that plugs the PostgreSQL
 * auth store into the Galasa framework's provider discovery mechanism.
 *
 * <p>The framework calls every registered {@link IAuthStoreRegistration} at
 * startup via {@code bundleContext.getAllServiceReferences(IAuthStoreRegistration.class.getName(), null)}.
 * This class checks whether the configured auth store URI uses the
 * {@code postgresql:} scheme; if so it constructs a {@link PostgresqlAuthStore}
 * and registers it. If the scheme belongs to a different provider (e.g.
 * {@code couchdb:}) this method returns silently.
 *
 * <p>Expected bootstrap property:
 * <pre>
 *   framework.auth.store=postgresql:jdbc:postgresql://galasa-postgresql:5432/galasa
 * </pre>
 */
@Component(service = { IAuthStoreRegistration.class })
public class PostgresqlAuthStoreRegistration implements IAuthStoreRegistration {

    /** URI scheme that identifies this provider. */
    public static final String URL_SCHEME = "postgresql";

    @Override
    public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
            throws AuthStoreException {

        URI uri = frameworkInitialisation.getAuthStoreUri();
        if (!isUriReferringToThisExtension(uri)) {
            return;
        }

        // Strip the outer "postgresql:" wrapper — the remainder is the JDBC URL
        // (which may embed credentials as jdbc:postgresql://user:pass@host:port/db).
        String jdbcUrl = uri.toString().substring((URL_SCHEME + ":").length());

        try {
            PostgresqlConnectionPoolManager pool = PostgresqlConnectionPoolManager.fromJdbcUrl(jdbcUrl);
            frameworkInitialisation.registerAuthStore(new PostgresqlAuthStore(pool));
        } catch (Exception e) {
            throw new AuthStoreException("Failed to initialise PostgreSQL Auth Store: " + e.getMessage(), e);
        }
    }

    /**
     * Returns {@code true} if the URI uses the {@code postgresql:} scheme.
     * A null URI returns {@code false} — no-op, another provider will handle it.
     */
    public boolean isUriReferringToThisExtension(URI uri) {
        return uri != null && URL_SCHEME.equals(uri.getScheme());
    }
}
