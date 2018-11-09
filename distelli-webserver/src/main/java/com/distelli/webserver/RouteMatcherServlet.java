package com.distelli.webserver;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

public class RouteMatcherServlet extends WebSocketServlet {
    public static final String MATCHED_ROUTE = "com.distelli.webserver.GenericMatchedRoute";
    private GenericRouteMatcher<GenericRequestHandler> routeMatcher;
    private WebSocketServletFactory webSocketServletFactory;
    public RouteMatcherServlet(GenericRouteMatcher<GenericRequestHandler> routeMatcher) {
        this.routeMatcher = routeMatcher;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        webSocketServletFactory = factory;
        factory.setCreator(this::createWebSocketAdapter);
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

    public boolean serviceError(Integer code, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String path = ( null == code ) ? "/500" : "/"+code;

        boolean upgradeRequest = webSocketServletFactory.isUpgradeRequest(request, response);
        HTTPMethod method = upgradeRequest ? HTTPMethod.WEBSOCKET : HTTPMethod.GET;

        GenericMatchedRoute<GenericRequestHandler> route = routeMatcher.match(method, path);
        if ( null == route || route.isDefaultRoute() ) {
            return false;
        }


        if ( upgradeRequest ) {
            request.setAttribute(GenericRequestHandler.class.getName(), route.getValue());
            webSocketServletFactory.acceptWebSocket(request, response);
        } else {
            route.getValue().service(request, response);
        }
        return true;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        HTTPMethod method = HTTPMethod.valueOf(request.getMethod().toUpperCase());
        boolean upgradeRequest = webSocketServletFactory.isUpgradeRequest(request, response);
        if ( upgradeRequest ) {
            method = HTTPMethod.WEBSOCKET;
        }

        GenericMatchedRoute<GenericRequestHandler> route = routeMatcher.match(method, request.getRequestURI());
        if ( null == route ) {
            throw new IllegalStateException("route matcher not configured with a default route!");
        }
        request.setAttribute(MATCHED_ROUTE, route);

        if ( upgradeRequest ) {
            request.setAttribute(GenericRequestHandler.class.getName(), route.getValue());
            webSocketServletFactory.acceptWebSocket(request, response);
        } else {
            route.getValue().service(request, response);
        }
    }

    private Object createWebSocketAdapter(ServletUpgradeRequest request, ServletUpgradeResponse response) {
        GenericRequestHandler handler = (GenericRequestHandler)request.getServletAttribute(
            GenericRequestHandler.class.getName());
        request.setServletAttribute(GenericRequestHandler.class.getName(), null);
        Object result = handler.createWebSocketAdapter(request, response);
        if ( null == result ) {
            // Avoid 503 response.
            try {
                response.sendError(404, "Not Found");
            } catch ( IOException ex ) {
                throw new UncheckedIOException(ex);
            }
        }
        return result;
    }
}
