package com.distelli.webserver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

public class FilterChainAdapter implements GenericRequestHandler {
    private FilterChain _chain;
    public FilterChainAdapter(FilterChain chain) {
        _chain = chain;
    }
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException
    {
        _chain.doFilter(request, response);
    }
}
