package com.distelli.webserver;

import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.ServerConnector;

public class WebServer implements Runnable
{
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private int _port;
    private WebServlet _appServlet = null;
    private Map<String, WebServlet> _webServlets = null;
    private Map<String, ServletHolder> _standardServlets = null;
    private String _path = null;
    private ErrorHandler _errorHandler = null;
    private Integer _sslPort;
    private SslContextFactory _sslContextFactory;

    public WebServer(int port, WebServlet appServlet, String path)
    {
        this(port, appServlet, path, null, null);
    }

    public WebServer(int port, WebServlet appServlet, String path, Integer sslPort, SslContextFactory sslContextFactory)
    {
        _port = port;
        _appServlet = appServlet;
        _path = path;
        if ( null == sslPort ^ null == sslContextFactory ) {
            throw new IllegalArgumentException(
                "If sslPort is not null, then sslContextFactory must also be non null. Got sslPort="+
                sslPort+" sslContextFactory="+sslContextFactory);
        }
        _sslPort = sslPort;
        _sslContextFactory = sslContextFactory;
    }

    public void addWebServlet(String path, WebServlet webServlet)
    {
        if(_webServlets == null)
            _webServlets = new HashMap<String, WebServlet>();
        _webServlets.put(path, webServlet);
    }

    public void addStandardServlet(String path, ServletHolder servletHolder)
    {
        if(_standardServlets == null)
            _standardServlets = new HashMap<String, ServletHolder>();
        _standardServlets.put(path, servletHolder);
    }

    public void setErrorHandler(ErrorHandler errorHandler)
    {
        _errorHandler = errorHandler;
    }

    public void run()
    {
        Thread.currentThread().setName("WebServer");
        try
        {
            Server server = new Server(_port);

            if ( null != _sslPort ) {
                ServerConnector connector = new ServerConnector(server, _sslContextFactory);
                connector.setPort(_sslPort);
                server.addConnector(connector);
            }

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setContextPath(_path);
            server.setHandler(context);

            ServletHolder servletHolder = new ServletHolder(_appServlet);
            context.addServlet(servletHolder, _path);
            if(_webServlets != null)
            {
                for(Map.Entry<String, WebServlet> entry : _webServlets.entrySet())
                {
                    String path = entry.getKey();
                    WebServlet webServlet = entry.getValue();
                    ServletHolder holder = new ServletHolder(webServlet);
                    context.addServlet(holder, path);
                }
            }

            if(_standardServlets != null)
            {
                for(Map.Entry<String, ServletHolder> entry : _standardServlets.entrySet())
                {
                    String path = entry.getKey();
                    ServletHolder holder = entry.getValue();
                    context.addServlet(holder, path);
                }
            }

            if(_errorHandler != null)
                context.setErrorHandler(_errorHandler);

            server.start();
            server.join();
        }
        catch(Throwable t)
        {
            throw(new RuntimeException(t));
        }
    }

    public void start()
    {
        run();
    }
}
