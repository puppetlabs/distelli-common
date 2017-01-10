package com.distelli.utils;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

public class CountingInputStream extends FilterInputStream {
    private long count=0;
    private long lastMark=0;
    public CountingInputStream(InputStream is) {
        super(is);
    }

    public long getCount() {
        return count;
    }

    @Override
    public void mark(int readlimit) {
        super.mark(readlimit);
        lastMark = 0;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        count -= lastMark;
        lastMark = 0;
    }

    @Override
    public int read() throws IOException {
        int result = super.read();
        if ( result >= 0 ) {
            lastMark++;
            count++;
        }
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if ( result > 0 ) {
            count += result;
            lastMark += result;
        }
        return result;
    }

    @Override
    public String toString() {
        return "CountingInputStream["+in+"] count="+count+" lastMark="+lastMark;
    }
}
