/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Seekable Byte Channel for writing PostgreSQL artifact binary data in memory buffers.
 */
public class PostgresqlRasWriteByteChannel implements SeekableByteChannel {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean open = true;

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        open = false;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        throw new IOException("Write-only byte channel");
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        int len = src.remaining();
        byte[] bytes = new byte[len];
        src.get(bytes);
        buffer.write(bytes);
        return len;
    }

    @Override
    public long position() throws IOException {
        return buffer.size();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        return this;
    }

    @Override
    public long size() throws IOException {
        return buffer.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        return this;
    }

    public byte[] getBytes() {
        return buffer.toByteArray();
    }
}
