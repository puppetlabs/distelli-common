package com.distelli.webserver;

import java.util.Map;
import java.util.HashMap;
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

public class WebServer implements Runnable
{
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private int _port = 5050;
    private WebServlet _appServlet = null;
    private Map<String, WebServlet> _webServlets = null;
    private Map<String, ServletHolder> _standardServlets = null;
    private String _path = null;
    private int _sessionMaxAge = 2592000; //default is 30 days

    public WebServer(int port, WebServlet appServlet, String path)
    {
        _port = port;
        _appServlet = appServlet;
        _path = path;
    }

    public void setSessionMaxAge(int sessionMaxAge)
    {
        _sessionMaxAge = sessionMaxAge;
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

    public void run()
    {
        Thread.currentThread().setName("WebServer");
        try
        {
            Server server = new Server(_port);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            SessionManager sessionManager = new HashSessionManager();
            context.setSessionHandler(new SessionHandler(sessionManager));
            context.setContextPath(_path);
            context.setInitParameter("org.eclipse.jetty.servlet.MaxAge", ""+_sessionMaxAge);
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
