package com.distelli.webserver;

import javax.servlet.http.HttpServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.ServerConnector;
import java.util.Set;
import javax.inject.Inject;
import java.nio.file.Paths;
import org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;

public class GuiceWebServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GuiceWebServer.class);
    private static final String STATIC_SERVLET_NAME = "static";
    private static final GenericRequestHandler DEFAULT_REQUEST_HANDLER = (method, req, res) -> {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("Dispatching to static servlet {}", req.getRequestURI());
        }
        req.getServletContext().getNamedDispatcher(STATIC_SERVLET_NAME)
        .forward(req, res);
    };

    private GenericRouteMatcher<GenericRequestHandler> routeMatcher;

    public static GenericRequestHandler getDefaultRequestHandler() {
        return DEFAULT_REQUEST_HANDLER;
    }

    @Inject
    protected GuiceWebServer(Set<GenericRouteSpec<GenericRequestHandler>> routeSpecs) {
        routeMatcher = new GenericRouteMatcher<GenericRequestHandler>();
        for ( GenericRouteSpec<GenericRequestHandler> routeSpec : routeSpecs ) {
            routeMatcher.add(routeSpec);
        }
        routeMatcher.setDefault(DEFAULT_REQUEST_HANDLER);
    }

    public void run() {
        run(null);
    }

    public void run(String portStr) {
        int port = (null == portStr) ? 8080 : Integer.parseInt(portStr);

        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.addErrorPage(404, "/404");
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setErrorHandler(errorHandler);
        context.setContextPath("/");
        context.addAliasCheck(new AllowSymLinkAliasChecker());

        ServletHolder servletHolder = new ServletHolder(new RouteMatcherServlet(routeMatcher));
        context.addServlet(servletHolder, "/");

        ServletHolder staticHolder = new ServletHolder(STATIC_SERVLET_NAME, DefaultServlet.class);
        staticHolder.setInitParameter("resourceBase", Paths.get("").toAbsolutePath().toString());
        staticHolder.setInitParameter("welcomeServlets", "true");
        staticHolder.setInitParameter("dirAllowed","true");
        staticHolder.setInitParameter("etags", "true");
        staticHolder.setInitParameter("gzip", "true");
        staticHolder.setInitParameter("aliases", "true");
        staticHolder.setInitParameter("cacheControl", "max-age=3600");
        // Don't really want this mapped to anything (only used for forwarding):
        context.addServlet(staticHolder, "");

        Server server = new Server(port);
        server.setHandler(context);

        try {
            server.start();
            LOG.info("Listening on port {}", port);
            server.join();
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }
}
