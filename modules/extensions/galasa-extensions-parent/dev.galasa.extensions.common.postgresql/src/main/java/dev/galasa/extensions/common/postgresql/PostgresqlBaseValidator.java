/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.extensions.common.postgresql;

/**
 * Preflight Configuration Validator for PostgreSQL stores.
 */
public class PostgresqlBaseValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static ValidationResult validateConfiguration(String host, int port, String database, String username) {
        if (host == null || host.trim().isEmpty()) {
            return new ValidationResult(false, "PostgreSQL host property is mandatory");
        }
        if (port <= 0 || port > 65535) {
            return new ValidationResult(false, "Invalid PostgreSQL port: " + port);
        }
        if (database == null || database.trim().isEmpty()) {
            return new ValidationResult(false, "PostgreSQL database name is mandatory");
        }
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, "PostgreSQL username property is mandatory");
        }
        return new ValidationResult(true, null);
    }
}
