/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class PostgresqlRasQueryBuilderTest {

    @Test
    public void testQueryBuilderSqlGeneration() {
        PostgresqlRasQueryBuilder builder = new PostgresqlRasQueryBuilder()
                .withRunId("run-123")
                .withStatus("PASSED")
                .withTestName("SimBank")
                .withLimit(20)
                .withOffset(40);

        String sql = builder.buildSqlQuery();
        assertThat(sql).contains("run_id = 'run-123'");
        assertThat(sql).contains("status = 'PASSED'");
        assertThat(sql).contains("test_name LIKE '%SimBank%'");
        assertThat(sql).contains("LIMIT 20 OFFSET 40");
    }

    @Test
    public void testReadWriteByteChannels() throws IOException {
        PostgresqlRasWriteByteChannel writeChannel = new PostgresqlRasWriteByteChannel();
        writeChannel.write(ByteBuffer.wrap("Hello PostgreSQL Artifacts".getBytes(StandardCharsets.UTF_8)));

        byte[] resultBytes = writeChannel.getBytes();
        assertThat(resultBytes).isNotEmpty();

        PostgresqlRasReadByteChannel readChannel = new PostgresqlRasReadByteChannel(resultBytes);
        ByteBuffer dst = ByteBuffer.allocate(100);
        int readBytes = readChannel.read(dst);

        assertThat(readBytes).isEqualTo(resultBytes.length);
        assertThat(new String(dst.array(), 0, readBytes, StandardCharsets.UTF_8)).isEqualTo("Hello PostgreSQL Artifacts");
    }
}
