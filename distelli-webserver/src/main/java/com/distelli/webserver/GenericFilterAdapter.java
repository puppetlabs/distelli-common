package com.distelli.webserver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericFilterAdapter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(GenericFilterAdapter.class);
    private GenericFilter _filter;
    public GenericFilterAdapter(GenericFilter filter) {
        _filter = filter;
    }
    @Override
    public void destroy() {}
    @Override
    public void init(FilterConfig config) {}
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
        if ( ! (request instanceof HttpServletRequest) ) {
            log.debug("doFilter() called with instance of "+request.getClass()+" which is not an HttpServletRequest");
            chain.doFilter(request, response);
            return;
        }
        if ( ! (response instanceof HttpServletResponse) ) {
            log.debug("doFilter() called with instance of "+response.getClass()+" which is not an HttpServletResponse");
            chain.doFilter(request, response);
            return;
        }
        _filter.filter((HttpServletRequest)request, (HttpServletResponse)response, new FilterChainAdapter(chain));
    }
}
