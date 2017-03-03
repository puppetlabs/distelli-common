package com.distelli.webserver;

import java.util.Map;

public class MatchedRoute extends GenericMatchedRoute<Class<? extends RequestHandler>>
{
    public MatchedRoute(GenericMatchedRoute<Class<? extends RequestHandler>> copy) {
        this(new RouteSpec(copy.getRouteSpec()), copy.getRouteParams());
    }

    public MatchedRoute(RouteSpec routeSpec, Map<String, String> routeParams)
    {
        super(routeSpec, routeParams);
    }

    public Class<? extends RequestHandler> getRequestHandler()
    {
        return getValue();
    }
}
