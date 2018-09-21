package com.distelli.webserver;

import com.distelli.utils.TopoSort;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkConnector;
import java.util.Collection;
import java.util.Arrays;
import java.util.Objects;
import org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuiceWebServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GuiceWebServer.class);
    private static final String STATIC_SERVLET_NAME = "static";
    private static final GenericRequestHandler DEFAULT_REQUEST_HANDLER = (req, res) -> {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug("Dispatching to static servlet {} with response={}", req.getRequestURI(), res);
        }
        req.getServletContext().getNamedDispatcher(STATIC_SERVLET_NAME)
        .forward(req, res);
    };

    private GenericRouteMatcher<GenericRequestHandler> routeMatcher;
    private List<GenericFilter> filters;

    public static GenericRequestHandler getDefaultRequestHandler() {
        return DEFAULT_REQUEST_HANDLER;
    }

    @Inject
    protected GuiceWebServer(
        Set<GenericRouteSpec<GenericRequestHandler>> routeSpecs,
        Map<String, GenericFilterSpec<GenericFilter>> filterSpecs)
    {
        routeMatcher = new GenericRouteMatcher<GenericRequestHandler>();
        for ( GenericRouteSpec<GenericRequestHandler> routeSpec : routeSpecs ) {
            routeMatcher.add(routeSpec);
        }
        routeMatcher.setDefault(DEFAULT_REQUEST_HANDLER);

        TopoSort<GenericFilterSpec<GenericFilter>> sorter = new TopoSort<>();
        for ( GenericFilterSpec<GenericFilter> filterSpec : filterSpecs.values() ) {
            LOG.debug("Injected filterSpec="+filterSpec);
            sorter.add(filterSpec);
            for ( String name : filterSpec.getAfter() ) {
                GenericFilterSpec<GenericFilter> dependency =
                    filterSpecs.get(name);
                if ( null != dependency ) {
                    sorter.add(filterSpec, dependency);
                } else {
                    // Might be fine, so simply log this:
                    LOG.debug("Filter '"+filterSpec.getName()+
                              "' must come after an undefined filter name='"+name+"'");
                }
            }
        }
        filters = new ArrayList<>();
        sorter.reverseSort((filterSpec) -> filters.add(filterSpec.getValue()));
    }

    public void run() {
        run(null);
    }

    public void run(String portStr) {
        int port = (null == portStr) ? 8080 : Integer.parseInt(portStr);
        Server server = createServer(port);
        try {
            server.start();
            LOG.info("Listening on port {}", getPort(server));
            server.join();
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    public static int getPort(Server server) {
        Connector[] connectors = server.getConnectors();
        if ( null == connectors || connectors.length < 1 ) {
            return 0;
        }
        return Arrays.asList(connectors).stream()
            .map(connector ->
                 ( connector instanceof NetworkConnector ) ? ((NetworkConnector)connector).getLocalPort() : null)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(0);
    }

    public Server createServer(int port) {
        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.addErrorPage(404, "/404");
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setErrorHandler(errorHandler);
        context.setContextPath("/");
        context.addAliasCheck(new AllowSymLinkAliasChecker());

        ServletHolder servletHolder = new ServletHolder(new RouteMatcherServlet(routeMatcher));
        context.addServlet(servletHolder, "/*");

        ServletHolder staticHolder = new ServletHolder(STATIC_SERVLET_NAME, DefaultServlet.class);
        staticHolder.setInitParameter("resourceBase", Paths.get("").toAbsolutePath().toString());
        staticHolder.setInitParameter("welcomeServlets", "true");
        staticHolder.setInitParameter("dirAllowed","true");
        staticHolder.setInitParameter("etags", "true");
        staticHolder.setInitParameter("gzip", "true");
        staticHolder.setInitParameter("aliases", "true");
        staticHolder.setInitParameter("cacheControl", "max-age=3600");
        context.addServlet(staticHolder, "/3C12AAD8-C66A-466C-8CD4-E6E6232315E7");

        for ( GenericFilter filter : filters ) {
            LOG.debug("Adding filter="+filter);
            FilterHolder holder = new FilterHolder(filter.toServletFilter());
            context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
        }

        Server server = new Server(port);
        server.setHandler(context);
        return server;
    }
}
