package com.distelli.webserver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.io.IOException;

@FunctionalInterface
public interface GenericFilter {
    public void filter(HttpServletRequest request, HttpServletResponse response, GenericRequestHandler chain)
        throws ServletException, IOException;

    public default Filter toServletFilter() {
        return new GenericFilterAdapter(this);
    }
}
