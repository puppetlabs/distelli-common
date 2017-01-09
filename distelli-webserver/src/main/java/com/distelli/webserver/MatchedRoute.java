package com.distelli.webserver;

import java.util.Map;

public class MatchedRoute
{
    private RouteSpec _routeSpec = null;
    private Map<String, String> _routeParams = null;

    public MatchedRoute(RouteSpec routeSpec, Map<String, String> routeParams)
    {
        _routeSpec = routeSpec;
        _routeParams = routeParams;
    }

    public String getPath()
    {
        return _routeSpec.getPath();
    }

    public HTTPMethod getHttpMethod()
    {
        return _routeSpec.getHttpMethod();
    }

    public Map<String, String> getParams()
    {
        return _routeParams;
    }

    public String getParam(String key)
    {
        if(_routeParams == null)
            return null;
        return _routeParams.get(key);
    }

    public Class<? extends RequestHandler> getRequestHandler()
    {
        return _routeSpec.getRequestHandler();
    }
}
