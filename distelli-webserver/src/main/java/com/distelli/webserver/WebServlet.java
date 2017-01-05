package com.distelli.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServlet extends HttpServlet
{
    private static final Logger log = LoggerFactory.getLogger(WebServlet.class);

    private RouteMatcher _routeMatcher = null;
    private RequestHandlerFactory _requestHandlerFactory = null;
    private RequestContextFactory _requestContextFactory = new RequestContextFactory();

    private RequestFilter[] _requestFilters = null;
    public WebServlet(RouteMatcher routeMatcher, RequestHandlerFactory requestHandlerFactory)
    {
        _routeMatcher = routeMatcher;
        _requestHandlerFactory = requestHandlerFactory;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.GET, request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.PUT, request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.POST, request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.DELETE, request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.TRACE, request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.HEAD, request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.OPTIONS, request, response);
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        handleRequest(HTTPMethod.PATCH, request, response);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        String httpMethod = request.getMethod();
        if(httpMethod.equalsIgnoreCase(HTTPMethod.PATCH.toString()))
            doPatch(request, response);
        else
            super.service(request, response);
    }

    public void setRequestFilters(RequestFilter... requestFilters)
    {
        _requestFilters = requestFilters;
    }

    public void setRequestHandlerFactory(RequestHandlerFactory requestHandlerFactory)
    {
        if(requestHandlerFactory == null)
            throw(new IllegalArgumentException("Request Handler Factory cannot be null"));
        _requestHandlerFactory = requestHandlerFactory;
    }

    public void setRequestContextFactory(RequestContextFactory requestContextFactory)
    {
        if(requestContextFactory == null)
            throw(new IllegalArgumentException("Request Context Factory cannot be null"));
        _requestContextFactory = requestContextFactory;
    }

    public void handleRequest(HTTPMethod httpMethod, HttpServletRequest request, HttpServletResponse response)
        throws ServletException
    {
        OutputStream out = null;
        WebResponse webResponse = null;
        RequestContext requestContext = null;
        boolean writeGZipped = false;
        try
        {
            MatchedRoute route = _routeMatcher.match(httpMethod, request.getRequestURI());
            if(route == null)
                throw(new IllegalStateException("No Route for Path: "+requestContext.getPath()));

            requestContext = _requestContextFactory.getRequestContext(httpMethod, request);
            webResponse = runFilters(route, requestContext);
            if(webResponse != null)
            {
                if(log.isDebugEnabled())
                    log.debug("Writing HttpStatusCode: "+webResponse.getHttpStatusCode()+
                              " for Request: "+requestContext.getRequestId());
                response.setStatus(webResponse.getHttpStatusCode());

                if(log.isDebugEnabled())
                    log.debug("Writing ContentType: "+webResponse.getContentType()+
                              " for Request: "+requestContext.getRequestId());

                response.setContentType(webResponse.getContentType());
                response.setCharacterEncoding("UTF-8");

                Map<String, String> responseHeaders = webResponse.getResponseHeaders();
                writeGZipped = requestContext.isGZipAccepted();
                if(responseHeaders != null && responseHeaders.size() > 0)
                {
                    for(Map.Entry<String, String> entry : responseHeaders.entrySet())
                    {
                        String headerName = entry.getKey();
                        if(writeGZipped && headerName != null && headerName.equalsIgnoreCase(WebConstants.CONTENT_ENCODING_HEADER))
                            writeGZipped = false;

                        response.setHeader(headerName, entry.getValue());
                    }
                }

                if(writeGZipped)
                    response.setHeader(WebConstants.CONTENT_ENCODING_HEADER, "gzip");

                for(Cookie cookie : webResponse.getCookies())
                    response.addCookie(cookie);
            }
        }
        catch(WebClientException wce)
        {
            //override the webResponse
            if(log.isDebugEnabled())
                log.debug("Caught WebClientException: "+wce.getMessage(), wce);
            webResponse = new WebResponse(400);
            String msg = wce.getMessage();
            if(msg != null && webResponse != null)
                webResponse.setResponseContent(msg.getBytes());
        }
        catch(Throwable t)
        {
            throw(new ServletException(t));
        }

        try
        {
            if(webResponse != null)
            {
                out = response.getOutputStream();
                if(writeGZipped) out = new GZIPOutputStream(out, true);
                webResponse.writeResponse(out);
                out.close();
            }
        }
        catch(Throwable t)
        {
            throw(new ServletException(t));
        } finally {
            if(webResponse != null) webResponse.close();
        }
    }

    private WebResponse runFilters(MatchedRoute route, RequestContext requestContext)
    {
        RequestFilterChain requestFilterChain = new RequestFilterSink(route, this);
        if(_requestFilters != null) {
            for(int i = _requestFilters.length - 1; i>=0; i--) {
                requestFilterChain = new RequestFilterHolder(_requestFilters[i], requestFilterChain);
            }
        }
        return requestFilterChain.filter(requestContext);
    }

    private WebResponse handleRequest(MatchedRoute route, RequestContext requestContext)
    {
        Map<String, String> routeParams = route.getParams();
        requestContext.addRouteParams(routeParams);

        RequestHandler requestHandler = _requestHandlerFactory.getRequestHandler(route);
        if(log.isDebugEnabled())
            log.debug("Calling RequestHandler: "+requestHandler.toString()+" for Request: "+requestContext.getRequestId());
        WebResponse webResponse = requestHandler.handleRequest(requestContext);
        if(log.isDebugEnabled())
            log.debug("Received WebResponse: "+webResponse+" for request: "+requestContext.getRequestId());
        return webResponse;
    }

    private static class RequestFilterHolder implements RequestFilterChain {
        private RequestFilter _filter;
        private RequestFilterChain _chain;
        public RequestFilterHolder(RequestFilter filter, RequestFilterChain chain) {
            _filter = filter;
            _chain = chain;
        }
        public WebResponse filter(RequestContext requestContext) {
            return _filter.filter(requestContext, _chain);
        }
    }

    private static class RequestFilterSink implements RequestFilterChain {
        private MatchedRoute _route;
        private WebServlet _servlet;
        public RequestFilterSink(MatchedRoute route, WebServlet servlet) {
            _route = route;
            _servlet = servlet;
        }

        public WebResponse filter(RequestContext requestContext) {
            return _servlet.handleRequest(_route, requestContext);
        }
    }
}
