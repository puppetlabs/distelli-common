package com.distelli.utils;

import java.io.FilterInputStream;
import java.io.InputStream;
import javax.crypto.Mac;
import java.io.IOException;

public class MacInputStream extends FilterInputStream {
    private Mac _mac;
    public MacInputStream(InputStream is, Mac mac) {
        super(is);
        _mac = mac;
    }

    public MacInputStream mac(Mac mac) {
        _mac = mac;
        return this;
    }

    @Override
    public int read() throws IOException {
        int ch = in.read();
        if ( null != _mac && ch >= 0 ) {
            _mac.update((byte)ch);
        }
        return ch;
    }

    @Override
    public int read(byte[] buff) throws IOException {
        return read(buff, 0, buff.length);
    }

    @Override
    public int read(byte[] buff, int off, int len) throws IOException {
        int result = in.read(buff, off, len);
        if ( null != _mac && result > 0 ) {
            _mac.update(buff, off, result);
        }
        return result;
    }
}
