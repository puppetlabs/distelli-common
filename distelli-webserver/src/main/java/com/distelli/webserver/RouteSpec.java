package com.distelli.webserver;

import javax.servlet.http.HttpServlet;

public class RouteSpec
{
    private String _path = null;
    private HTTPMethod _httpMethod = null;
    private Class<? extends RequestHandler> _requestHandler = null;

    public static class Builder {
        private String _path = null;
        private HTTPMethod _httpMethod = null;
        private Class<? extends RequestHandler> _requestHandler = null;

        public Builder withPath(String path) { _path = path; return this;}
        public Builder withHTTPMethod(HTTPMethod httpMethod) { _httpMethod = httpMethod; return this;}
        public Builder withRequestHandler(Class<? extends RequestHandler> requestHandler) {_requestHandler = requestHandler; return this;}
        public RouteSpec build() {
            RouteSpec routeSpec = new RouteSpec();
            routeSpec._path = _path;
            routeSpec._httpMethod = _httpMethod;
            routeSpec._requestHandler = _requestHandler;
            return routeSpec;
        }
    }

    public String getPath() {
        return this._path;
    }

    public HTTPMethod getHttpMethod() {
        return this._httpMethod;
    }

    public Class<? extends RequestHandler> getRequestHandler()
    {
        return _requestHandler;
    }

    @Override
    public String toString()
    {
        return String.format("RouteSpec[path=%s, httpMethod=%s, requestHandler=%s",
                             _path,
                             _httpMethod,
                             _requestHandler);
    }
}
