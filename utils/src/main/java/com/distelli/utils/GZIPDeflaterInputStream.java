package com.distelli.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.InputStream;
import java.io.FilterInputStream;
//import java.util.zip.DeflaterInputStream;
import java.util.zip.Deflater;
import java.util.zip.CRC32;
import java.io.IOException;

public class GZIPDeflaterInputStream extends FilterInputStream {
    // http://www.zlib.org/rfc-gzip.html#header-trailer
    private static final byte[] HEADER = new byte[] {
        (byte)0x1f,
        (byte)0x8b,
        Deflater.DEFLATED,
        0, 0, 0, 0, 0, 0, 0 };

    private CRC32 crc = new CRC32();
    private ByteBuffer buff = ByteBuffer.allocateDirect(512)
        .order(ByteOrder.LITTLE_ENDIAN);
    private boolean isTrailer = false;
    private Deflater deflater;
    private byte[] readBuff = new byte[1];
    private byte[] deflaterBuff = new byte[512];

    public GZIPDeflaterInputStream(InputStream in) {
        super(in);
        deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        buff.put(HEADER);
        buff.flip();
    }

    @Override
    public synchronized int read(byte b[], int off, int len) throws IOException {
        ensureOpen();
        if ( null == b ) {
            throw new NullPointerException("Null buffer for read");
        } else if ( off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if ( len == 0 ) {
            return 0;
        }

        int total = 0;

        // Satisfy with anything in buff first:
        int read = buff.remaining();
        if ( read > 0 ) {
            if ( len < read ) read = len;
            buff.get(b, off, read);
            len -= read;
            off += read;
            total += read;
        }

        // Satisfy with deflater:
        while ( len > 0 && !deflater.finished() ) {
            // Read data from the input stream
            if ( deflater.needsInput() ) {
                read = in.read(deflaterBuff, 0, deflaterBuff.length);
                if ( read < 0 ) {
                    deflater.finish();
                } else if ( read > 0 ) {
                    crc.update(deflaterBuff, 0, read);
                    deflater.setInput(deflaterBuff, 0, read);
                }
            }

            // Compress the input data, filling the read buffer
            read = deflater.deflate(b, off, len);
            len -= read;
            off += read;
            total += read;
        }
        if ( len > 0 && ! isTrailer ) {
            // Recurse...
            isTrailer = true;
            buff.clear();
            buff.putInt((int)crc.getValue());
            buff.putInt(deflater.getTotalIn());
            buff.flip();
            return total + read(b, off, len);
        }
        return ( total > 0 ) ? total : -1;
    }

    @Override
    public synchronized void close() throws IOException {
        if ( null == in ) return;
        try {
            deflater.end();
            in.close();
        } finally {
            in = null;
        }
    }

    // Delegate to read(byte[],int,int):
    @Override
    public synchronized long skip(long n) throws IOException {
        if ( n < 0 ) {
            throw new IllegalArgumentException("negative skip length");
        }

        int total = (int)Math.min(n, Integer.MAX_VALUE);
        long cnt = 0;
        byte[] skipBuff = new byte[512];
        while ( total > 0 ) {
            int buffLen = skipBuff.length;
            if ( total < buffLen ) buffLen = total;
            int len = read(skipBuff, 0, buffLen);
            if ( len < 0 ) break;
            cnt += len;
            total -= len;
        }
        return cnt;
    }

    // Delegate to read(byte[],int,int):
    @Override
    public synchronized int read() throws IOException {
        int len = read(readBuff, 0, 1);
        if ( len <= 0 ) return -1;
        return readBuff[0] & 0xFF;
    }

    // Delegate to read(byte[],int,int):
    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public synchronized int available() throws IOException {
        ensureOpen();
        return ( isTrailer && ! buff.hasRemaining() ) ? 0 : 1;
    }

    private void ensureOpen() throws IOException {
        if ( null == in ) {
            throw new IOException("Stream closed");
        }
    }
}
