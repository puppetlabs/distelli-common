package com.distelli.webserver;

import java.util.Map;

public class GenericMatchedRoute<T>
{
    private GenericRouteSpec<T> _routeSpec = null;
    private Map<String, String> _routeParams = null;

    public GenericMatchedRoute(GenericRouteSpec<T> routeSpec, Map<String, String> routeParams)
    {
        _routeSpec = routeSpec;
        _routeParams = routeParams;
    }

    public GenericRouteSpec<T> getRouteSpec() {
        return _routeSpec;
    }

    public Map<String, String> getRouteParams() {
        return _routeParams;
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

    public T getValue()
    {
        return _routeSpec.getValue();
    }
}
