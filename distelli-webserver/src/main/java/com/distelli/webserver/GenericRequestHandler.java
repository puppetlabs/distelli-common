package com.distelli.webserver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

@FunctionalInterface
public interface GenericRequestHandler {
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException;
}
