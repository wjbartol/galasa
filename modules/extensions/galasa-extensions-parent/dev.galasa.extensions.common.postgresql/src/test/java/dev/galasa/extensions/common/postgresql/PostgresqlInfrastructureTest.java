/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class PostgresqlInfrastructureTest {

    @Test
    public void testConnectionPoolManagerProperties() {
        PostgresqlConnectionPoolManager poolManager = new PostgresqlConnectionPoolManager(
                "localhost", 5432, "galasa", "galasa_user", "secret"
        );
        poolManager.setSslEnabled(true);

        assertThat(poolManager.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/galasa");
        
        Properties props = poolManager.buildConnectionProperties();
        assertThat(props.getProperty("user")).isEqualTo("galasa_user");
        assertThat(props.getProperty("password")).isEqualTo("secret");
        assertThat(props.getProperty("ssl")).isEqualTo("true");
    }

    @Test
    public void testBaseValidator() {
        PostgresqlBaseValidator.ValidationResult validRes = PostgresqlBaseValidator.validateConfiguration(
                "localhost", 5432, "galasa", "galasa_user"
        );
        assertThat(validRes.isValid()).isTrue();

        PostgresqlBaseValidator.ValidationResult invalidRes = PostgresqlBaseValidator.validateConfiguration(
                "", 5432, "galasa", "galasa_user"
        );
        assertThat(invalidRes.isValid()).isFalse();
        assertThat(invalidRes.getErrorMessage()).contains("host");
    }

    @Test
    public void testExecuteWithRetrySuccess() throws Exception {
        PostgresqlConnectionPoolManager poolManager = new PostgresqlConnectionPoolManager(
                "localhost", 5432, "galasa", "galasa_user", "secret"
        );
        PostgresqlStore store = new PostgresqlStore(poolManager) {};

        AtomicInteger counter = new AtomicInteger(0);
        String result = store.executeWithRetry(() -> {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("Transient failure");
            }
            return "SUCCESS";
        });

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(counter.get()).isEqualTo(2);
        // Offline probe gracefully handles connection failure
        assertThat(store.pingHealthProbe()).isFalse();
    }
}
