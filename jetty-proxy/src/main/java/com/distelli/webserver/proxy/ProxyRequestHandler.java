package com.distelli.webserver.proxy;

import com.distelli.webserver.GenericRequestHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.inject.Inject;
import javax.servlet.ServletException;
import java.io.IOException;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import java.util.Enumeration;
import java.util.Collections;
import javax.servlet.ServletException;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.inject.Singleton;

@Singleton
public class ProxyRequestHandler implements GenericRequestHandler {
    private AsyncProxyServlet _servlet;

    @Inject
    private ProxyConfig _config;

    @Inject
    private ProxyWebSocketAdapter.Factory _proxyWebSocketAdapterFactory;

    @Inject
    protected ProxyRequestHandler() {}

    private static class ServletConfigWrapper implements ServletConfig {
        private Map<String, String> _initParams = new LinkedHashMap<>();
        private ServletContext _context;
        private ServletConfigWrapper(ProxyConfig config, ServletContext context) {
            _initParams.put("proxyTo", ""+config.getProxyTo());
            _context = context;
        }
        @Override
        public String getServletName() {
            return "ProxyRequestHandler";
        }
        @Override
        public ServletContext getServletContext() {
            return _context;
        }
        @Override
        public String getInitParameter(String name) {
            return _initParams.get(name);
        }
        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(_initParams.keySet());
        }
    }

    private synchronized AsyncProxyServlet getServlet(ServletContext context) throws ServletException {
        // TODO: Support server start listeners so we can do this on server start,
        // not during the first request:
        if ( null == _servlet ) {
            _servlet = new AsyncProxyServlet.Transparent();
            _servlet.init(new ServletConfigWrapper(_config, context));
        }
        return _servlet;
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        if ( ! request.isAsyncStarted() ) {
            getServlet(request.getServletContext()).service(request, response);
        }
    }

    @Override
    public Object createWebSocketAdapter(ServletUpgradeRequest request, ServletUpgradeResponse response) {
        return _proxyWebSocketAdapterFactory.create(_config, request, response);
    }
}
