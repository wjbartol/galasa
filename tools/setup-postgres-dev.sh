#!/usr/bin/env bash
# ==============================================================================
#  🐘 Galasa Ecosystem - Repeatable PostgreSQL Environment Setup Script
# ==============================================================================
#  Purpose: Automates local PostgreSQL service startup, role/database creation,
#           permissions, and DDL schema initialization.
#  Usage:   ./galasa/tools/setup-postgres-dev.sh
# ==============================================================================

set -euo pipefail

DB_NAME="${DB_NAME:-galasadb}"
DB_USER="${DB_USER:-galasa}"
DB_PASS="${DB_PASS:-galasapassword}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"

echo "=============================================================================="
echo " 🐘 GALASA REPEATABLE POSTGRESQL ENVIRONMENT SETUP"
echo "=============================================================================="
echo "Target Host     : ${DB_HOST}:${DB_PORT}"
echo "Target Database : ${DB_NAME}"
echo "Target User     : ${DB_USER}"
echo "------------------------------------------------------------------------------"

# 1. Start native PostgreSQL service if not running
echo "[1/4] Checking local PostgreSQL service..."
if command -v service >/dev/null 2>&1; then
    if ! sudo service postgresql status >/dev/null 2>&1; then
        echo "Starting local PostgreSQL service..."
        sudo service postgresql start
    else
        echo "PostgreSQL service is already running."
    fi
elif command -v systemctl >/dev/null 2>&1; then
    if ! systemctl is-active --quiet postgresql; then
        echo "Starting postgresql systemd service..."
        sudo systemctl start postgresql
    fi
fi

# 2. Idempotent User & Database Creation
echo "[2/4] Initializing database '${DB_NAME}' and user '${DB_USER}'..."
sudo -u postgres psql -v ON_ERROR_STOP=1 <<EOF
DO \$\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${DB_USER}') THEN
      CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}';
   ELSE
      ALTER ROLE ${DB_USER} WITH PASSWORD '${DB_PASS}';
   END IF;
END
\$\$;

SELECT 'CREATE DATABASE ${DB_NAME}'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}')\gexec

GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};
EOF

# Grant schema privileges for PostgreSQL 15+
sudo -u postgres psql -d "${DB_NAME}" -v ON_ERROR_STOP=1 <<EOF
GRANT ALL ON SCHEMA public TO ${DB_USER};
ALTER SCHEMA public OWNER TO ${DB_USER};
EOF

# 3. Test Connection
echo "[3/4] Testing connection to ${DB_HOST}:${DB_PORT}/${DB_NAME} as user '${DB_USER}'..."
export PGPASSWORD="${DB_PASS}"
if psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT 1;" >/dev/null 2>&1; then
    echo "✅ Connection succeeded!"
else
    echo "❌ Connection failed. Check pg_hba.conf or host configuration."
    exit 1
fi

# 4. Initialize DDL Schema Tables
echo "[4/4] Applying DDL table schema initialization..."
psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 <<EOF
-- RAS Runs Table
CREATE TABLE IF NOT EXISTS ras_runs (
  run_id VARCHAR(128) PRIMARY KEY,
  test_name VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  run_duration_ms BIGINT DEFAULT 0,
  artifact_size_bytes BIGINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- RAS Artifacts Table
CREATE TABLE IF NOT EXISTS ras_artifacts (
  artifact_id VARCHAR(128) PRIMARY KEY,
  run_id VARCHAR(128) NOT NULL,
  artifact_name VARCHAR(256) NOT NULL,
  content BYTEA,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- CPS Configuration Properties Table
CREATE TABLE IF NOT EXISTS cps_properties (
  property_key VARCHAR(256) PRIMARY KEY,
  property_value TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- DSS Distributed Locks Table
CREATE TABLE IF NOT EXISTS dss_locks (
  lock_key VARCHAR(256) PRIMARY KEY,
  holder_id VARCHAR(256) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Auth Tokens Table
CREATE TABLE IF NOT EXISTS auth_tokens (
  token_id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  token_data TEXT NOT NULL,
  expires_at TIMESTAMP,
  is_revoked BOOLEAN DEFAULT FALSE
);

-- Auth Credentials Table
CREATE TABLE IF NOT EXISTS auth_credentials (
  cred_id VARCHAR(128) PRIMARY KEY,
  cred_type VARCHAR(64) NOT NULL,
  cred_payload TEXT NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
EOF

echo "=============================================================================="
echo " 🎉 POSTGRESQL ENVIRONMENT READY FOR GALASA EXECUTION"
echo "=============================================================================="
