package com.distelli.webserver;

public interface RequestHandlerFactory
{
    public RequestHandler getRequestHandler(MatchedRoute route);
}
