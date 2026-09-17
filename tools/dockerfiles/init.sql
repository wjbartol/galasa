-- ==============================================================================
-- 🐘 Galasa Ecosystem - PostgreSQL Initial Schema SQL Script
-- ==============================================================================

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
