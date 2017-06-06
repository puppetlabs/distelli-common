package com.distelli.utils;

import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

public class LinesInputStream extends InputStream {
    private List<byte[]> lines = new ArrayList<>();
    private int totalBytes = 0;

    private int markLine = 0;
    private int markOffset = 0;

    private int line = 0;
    private int offset = 0;

    public synchronized void add(byte[] line) {
        if ( null == line ) return;
        totalBytes += line.length;
        lines.add(line);
    }

    @Override
    public synchronized int available() {
        if ( 0 == line && 0 == offset ) return totalBytes;
        if ( line >= lines.size() ) return 0;
        int available = 0;
        for ( int cur=lines.size()-1; cur > line; cur-- ) {
            available += lines.get(cur).length;
        }
        return available + lines.get(line).length - offset;
    }

    @Override
    public void close() {}

    @Override
    public synchronized void mark(int readlimit) {
        markLine = line;
        markOffset = offset;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized int read() {
        for (; line < lines.size(); line++, offset=0 ) {
            byte[] bytes = lines.get(line);
            if ( bytes.length - offset > 0 ) {
                return bytes[offset++];
            }
        }
        return -1;
    }

    @Override
    public synchronized int read(byte[] buff) {
        if ( null == buff ) return 0;
        return read(buff, 0, buff.length);
    }

    @Override
    public synchronized int read(byte[] buff, int off, int len) {
        if ( null == buff ) return 0;
        if ( buff.length < off + len ) throw new ArrayIndexOutOfBoundsException();

        int originalOff = off;
        for (; line < lines.size(); line++, offset=0 ) {
            byte[] bytes = lines.get(line);
            int remainder = bytes.length - offset;
            if ( remainder > 0 ) {
                if ( remainder > len ) remainder = len;

                System.arraycopy(bytes, offset, buff, off, remainder);
                off += remainder;
                len -= remainder;

                if ( 0 == len ) {
                    offset += remainder;
                    return off - originalOff;
                }
            }
        }
        if ( originalOff == off ) return -1;
        return off - originalOff;
    }

    @Override
    public synchronized void reset() {
        line = markLine;
        offset = markOffset;
    }

    @Override
    public synchronized long skip(long cnt) {
        long skipped = 0;
        for (; cnt > 0 && line < lines.size(); line++, offset=0 ) {
            long remainder = lines.get(line).length - offset;
            if ( cnt < remainder ) {
                offset += cnt;
                return skipped + cnt;
            }
            skipped += remainder;
            cnt -= remainder;
        }
        return skipped;
    }
}
