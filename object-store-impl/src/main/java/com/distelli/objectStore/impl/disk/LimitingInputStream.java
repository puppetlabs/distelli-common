/*
  $Id: $
  @file LimitingInputStream.java
  @brief Contains the LimitingInputStream.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.objectStore.impl.disk;

import java.io.IOException;
import java.io.InputStream;

public class LimitingInputStream extends InputStream
{
    private InputStream _in;
    private long _maxBytesToRead;
    private long _totalBytesRead = 0;

    public LimitingInputStream(InputStream in, long maxBytesToRead)
    {
        _in = in;
        _maxBytesToRead = maxBytesToRead;
    }

    @Override
    public int available()
        throws IOException
    {
        int available = _in.available();
        int remaining = (int)(_maxBytesToRead - _totalBytesRead);
        if(available <= remaining)
            return available;
        return 0;
    }

    @Override
    public void close()
        throws IOException
    {
        _in.close();
    }

    @Override
    public void mark(int readLimit)
    {
        throw(new UnsupportedOperationException());
    }

    @Override
    public boolean markSupported()
    {
        return false;
    }

    @Override
    public int read()
        throws IOException
    {
        if(_totalBytesRead >= _maxBytesToRead)
            return -1;
        int data = _in.read();
        _maxBytesToRead++;
        return data;
    }

    @Override
    public int read(byte[] b)
        throws IOException
    {
        if(_totalBytesRead >= _maxBytesToRead)
            return -1;
        int bytesRead = _in.read(b);
        long totalRead = _totalBytesRead+bytesRead;
        if(totalRead <= _maxBytesToRead)
        {
            _totalBytesRead = totalRead;
            return bytesRead;
        }

        long remaining = _maxBytesToRead - _totalBytesRead;
        _totalBytesRead = totalRead;
        if(remaining == 0)
            return -1;
        return (int)remaining;
    }

    @Override
    public int read(byte[] b, int off, int len)
        throws IOException
    {
        int currentlyRead = _in.read(b, off, len);
        long totalRead = _totalBytesRead+currentlyRead;
        if(totalRead <= _maxBytesToRead)
        {
            _totalBytesRead = totalRead;
            return currentlyRead;
        }

        long remaining = _maxBytesToRead - _totalBytesRead;
        _totalBytesRead = totalRead;
        if(remaining == 0)
            return -1;
        return (int)remaining;
    }

    @Override
    public void reset()
    {
        throw(new UnsupportedOperationException());
    }

    @Override
    public long skip(long n)
    {
        throw(new UnsupportedOperationException());
    }
}
