/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL Query Builder for PostgreSQL Result Archive Store queries.
 */
public class PostgresqlRasQueryBuilder {

    private String runId;
    private String status;
    private String testName;
    private int limit = 50;
    private int offset = 0;

    public PostgresqlRasQueryBuilder withRunId(String runId) {
        this.runId = runId;
        return this;
    }

    public PostgresqlRasQueryBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public PostgresqlRasQueryBuilder withTestName(String testName) {
        this.testName = testName;
        return this;
    }

    public PostgresqlRasQueryBuilder withLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public PostgresqlRasQueryBuilder withOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public String buildSqlQuery() {
        StringBuilder sql = new StringBuilder("SELECT run_id, status, test_name, result, created_at FROM galasa_runs WHERE 1=1");
        if (runId != null && !runId.isEmpty()) {
            sql.append(" AND run_id = '").append(runId.replace("'", "''")).append("'");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = '").append(status.replace("'", "''")).append("'");
        }
        if (testName != null && !testName.isEmpty()) {
            sql.append(" AND test_name LIKE '%").append(testName.replace("'", "''")).append("%'");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(limit).append(" OFFSET ").append(offset);
        return sql.toString();
    }
}
