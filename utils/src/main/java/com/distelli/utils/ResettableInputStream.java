package com.distelli.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

public class ResettableInputStream extends InputStream {
    private static final int BUFFER_SIZE = 1024*1024;
    private InputStream _inputStream;
    private FileChannel _fileChannel;
    private long _mark;
    private long _hwm; // high water mark (updated on reset).

    public ResettableInputStream(InputStream inputStream) throws IOException {
        _inputStream = inputStream;
        _fileChannel = FileChannel.open(
            File.createTempFile("ResettableInputStream",null).toPath(),
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.DELETE_ON_CLOSE);
        _mark = _fileChannel.position();
        _hwm = _mark;
    }

    //////////////////////////////////////////////////////////////////////
    // Obtain the underlying file channel size:
    public long size() throws IOException {
        return _fileChannel.size();
    }

    @Override
    public int read() throws IOException {
        byte[] tmp = new byte[1];
        int count = read(tmp);
        if ( -1 != count ) {
            return tmp[0];
        } else {
            return count;
        }
    }

    @Override
    public int read(byte[] chunk) throws IOException {
        return read(chunk, 0, chunk.length);
    }

    @Override
    public synchronized int read(byte[] chunk, int offset, int len) throws IOException {
        // Grab from _fileChannel:
        int fcSize = (int)Math.min(_hwm - _fileChannel.position(), (long)len);
        if ( fcSize > 0 ) {
            int size = _fileChannel.read(ByteBuffer.wrap(chunk, offset, fcSize));
            if ( fcSize != size ) {
                throw new IllegalStateException("Expected to read "+fcSize+", but got "+size);
            }
            if ( len == fcSize ) return len;
            offset += fcSize;
            len -= fcSize;
        } else {
            fcSize = 0;
        }
        // Grab from _inputStream:
        int isSize = _inputStream.read(chunk, offset, len);
        if ( isSize > 0 ) {
            // Save to fileChannel:
            ByteBuffer out = ByteBuffer.wrap(chunk, offset, isSize);
            try {
                int size = _fileChannel.write(out);
                if ( isSize != size ) {
                    throw new IllegalStateException("Expected to write "+isSize+", but got "+size+" check for sufficient disk space");
                }
            } catch ( Throwable ex ) {
                ex.printStackTrace();
                throw ex;
            }
        } else if ( isSize < 0 ) {
            if ( fcSize > 0 ) return fcSize;
            return isSize;
        }
        return isSize + fcSize;
    }

    @Override
    public synchronized int available() throws IOException {
        long pos = _fileChannel.position();
        return _inputStream.available() +
            ( _hwm > pos ? Math.toIntExact(_hwm - pos) : 0 );
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            _inputStream.close();
        } finally {
            _fileChannel.close();
        }
    }

    @Override
    public synchronized void mark(int max) {
        try {
            _mark = _fileChannel.position();
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        _hwm = Math.max(_hwm, _fileChannel.position());
        _fileChannel.position(_mark);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized long skip(long cnt) throws IOException {
        long fcSize = Math.min(_hwm - _fileChannel.position(), cnt);
        long skipped = 0;
        if ( fcSize > 0 ) {
            _fileChannel.position(_fileChannel.position() + fcSize);
            if ( cnt == fcSize ) return cnt;
            skipped += fcSize;
            cnt -= fcSize;
        }

        // Read so we save it to the file channel:
        byte[] buff = new byte[cnt < BUFFER_SIZE ? Math.toIntExact(cnt) : BUFFER_SIZE];
        while ( cnt > 0 ) {
            int size = read(buff);
            if ( size < 0 ) return skipped;
            skipped += size;
            cnt -= size;
        }
        return skipped;
    }
}
