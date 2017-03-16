package com.distelli.webserver;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

public class RouteMatcherServlet extends HttpServlet implements GenericRequestHandler {
    public static final String MATCHED_ROUTE = "com.distelli.webserver.MatchedRoute";
    private GenericRouteMatcher<GenericRequestHandler> routeMatcher;
    public RouteMatcherServlet(GenericRouteMatcher<GenericRequestHandler> routeMatcher) {
        this.routeMatcher = routeMatcher;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        service(request, response);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        HTTPMethod method = HTTPMethod.valueOf(request.getMethod().toUpperCase());
        GenericMatchedRoute<GenericRequestHandler> route = routeMatcher.match(method, request.getRequestURI());
        if ( null == route ) {
            throw new IllegalStateException("route matcher not configured with a default route!");
        }
        request.setAttribute(MATCHED_ROUTE, route);
        route.getValue().service(request, response);
    }
}
