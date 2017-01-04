package com.distelli.webserver;

import java.io.IOException;
import java.io.OutputStream;

public interface ResponseWriter
{
    public void writeResponse(OutputStream out)
        throws IOException;
}
