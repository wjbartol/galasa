/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.postgresql.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Seekable Byte Channel for reading PostgreSQL artifact binary data.
 */
public class PostgresqlRasReadByteChannel implements SeekableByteChannel {

    private final byte[] data;
    private int position = 0;
    private boolean open = true;

    public PostgresqlRasReadByteChannel(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

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
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
        if (position >= data.length) {
            return -1;
        }
        int len = Math.min(dst.remaining(), data.length - position);
        dst.put(data, position, len);
        position += len;
        return len;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new IOException("Read-only byte channel");
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        if (newPosition < 0 || newPosition > data.length) {
            throw new IllegalArgumentException("Invalid position: " + newPosition);
        }
        this.position = (int) newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        return data.length;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException("Read-only byte channel");
    }
}
