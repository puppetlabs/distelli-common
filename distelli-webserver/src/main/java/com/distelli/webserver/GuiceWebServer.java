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

public class GuiceWebServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GuiceWebServer.class);
    private static final String STATIC_SERVLET_NAME = "static";
    private GenericRouteMatcher<GenericRequestHandler> routeMatcher;

    @Inject
    protected GuiceWebServer(Set<GenericRouteSpec<GenericRequestHandler>> routeSpecs) {
        routeMatcher = new GenericRouteMatcher<GenericRequestHandler>();
        for ( GenericRouteSpec<GenericRequestHandler> routeSpec : routeSpecs ) {
            routeMatcher.add(routeSpec);
        }
        routeMatcher.setDefault(
            (method, req, res) -> {
                req.getServletContext()
                    .getNamedDispatcher(STATIC_SERVLET_NAME)
                    .forward(req, res);
            });
    }

    private static int SESSION_MAX_AGE = 2592000; //default is 30 days

    public void run() {
        run(null);
    }

    public void run(String portStr) {
        int port = (null == portStr) ? 8080 : Integer.parseInt(portStr);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setSessionHandler(new SessionHandler(new HashSessionManager()));
        context.setContextPath("/");
        context.setInitParameter("org.eclipse.jetty.servlet.MaxAge", ""+SESSION_MAX_AGE);
        context.addAliasCheck(new AllowSymLinkAliasChecker());

        ServletHolder servletHolder = new ServletHolder(new RouteMatcherServlet(routeMatcher));
        context.addServlet(servletHolder, "/");

        ServletHolder staticHolder = new ServletHolder(STATIC_SERVLET_NAME, DefaultServlet.class);
        staticHolder.setInitParameter("resourceBase", Paths.get("").toAbsolutePath().toString());
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
            LOG.info("Listening on port "+port);
            server.join();
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }
}
