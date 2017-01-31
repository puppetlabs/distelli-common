package com.distelli.webserver;

import java.util.Collections;
import java.util.Arrays;
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
import java.util.Collection;

public class WebServlet<RCTX extends RequestContext> extends HttpServlet
{
    private static final Logger log = LoggerFactory.getLogger(WebServlet.class);

    private RouteMatcher _routeMatcher = null;
    private RequestHandlerFactory _requestHandlerFactory = null;
    private RequestContextFactory<RCTX> _requestContextFactory = null;

    private List<RequestFilter<RCTX>> _requestFilters = Collections.emptyList();
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

    public void setRequestFilters(RequestFilter<RCTX>... requestFilters)
    {
        _requestFilters = Arrays.asList(requestFilters);
    }

    public void setRequestFilters(Collection<RequestFilter<RCTX>> requestFilters)
    {
        _requestFilters = new ArrayList<RequestFilter<RCTX>>(requestFilters);
    }

    public void setRequestHandlerFactory(RequestHandlerFactory requestHandlerFactory)
    {
        if(requestHandlerFactory == null)
            throw(new IllegalArgumentException("Request Handler Factory cannot be null"));
        _requestHandlerFactory = requestHandlerFactory;
    }

    public void setRequestContextFactory(RequestContextFactory<RCTX> requestContextFactory)
    {
        if(requestContextFactory == null)
            throw(new IllegalArgumentException("Request Context Factory cannot be null"));
        _requestContextFactory = requestContextFactory;
    }

    public void handleRequest(HTTPMethod httpMethod, HttpServletRequest request, HttpServletResponse response)
        throws ServletException
    {
        if ( null == _requestContextFactory ) {
            throw new IllegalStateException("WebServlet must be initialized with a RequestContextFactory.");
        }

        OutputStream out = null;
        WebResponse webResponse = null;
        RCTX requestContext = null;
        boolean writeGZipped = false;
        try
        {
            requestContext = _requestContextFactory.getRequestContext(httpMethod, request);
            MatchedRoute route = _routeMatcher.match(httpMethod, requestContext.getPath());
            if(route == null)
                throw(new IllegalStateException("No Route for Path: "+requestContext.getPath()));

            requestContext.setMatchedRoute(route);
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
                response.setCharacterEncoding(webResponse.getCharacterEncoding());

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
            log.error(t.getMessage(), t);
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

    private WebResponse runFilters(MatchedRoute route, RCTX requestContext)
    {
        RequestFilterChain<RCTX> requestFilterChain = new RequestFilterSink<RCTX>(route, this);
        for(int i = _requestFilters.size() - 1; i>=0; i--) {
            requestFilterChain = new RequestFilterHolder<RCTX>(_requestFilters.get(i), requestFilterChain);
        }
        return requestFilterChain.filter(requestContext);
    }

    private WebResponse handleRequest(MatchedRoute route, RCTX requestContext)
    {
        RequestHandler requestHandler = _requestHandlerFactory.getRequestHandler(route);
        if(log.isDebugEnabled())
            log.debug("Calling RequestHandler: "+requestHandler.toString()+" for Request: "+requestContext.getRequestId());
        WebResponse webResponse = requestHandler.handleRequest(requestContext);
        if(log.isDebugEnabled())
            log.debug("Received WebResponse: "+webResponse+" for request: "+requestContext.getRequestId());
        return webResponse;
    }

    private static class RequestFilterHolder<RCTX extends RequestContext> implements RequestFilterChain<RCTX> {
        private RequestFilter<RCTX> _filter;
        private RequestFilterChain<RCTX> _chain;
        public RequestFilterHolder(RequestFilter<RCTX> filter, RequestFilterChain<RCTX> chain) {
            _filter = filter;
            _chain = chain;
        }
        public WebResponse filter(RCTX requestContext) {
            return _filter.filter(requestContext, _chain);
        }
    }

    private static class RequestFilterSink<RCTX extends RequestContext> implements RequestFilterChain<RCTX> {
        private MatchedRoute _route;
        private WebServlet _servlet;
        public RequestFilterSink(MatchedRoute route, WebServlet servlet) {
            _route = route;
            _servlet = servlet;
        }

        public WebResponse filter(RCTX requestContext) {
            return _servlet.handleRequest(_route, requestContext);
        }
    }
}
