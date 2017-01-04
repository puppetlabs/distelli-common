package com.distelli.webserver;

import javax.servlet.http.HttpServlet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

/**
   The route matcher matches HTTP routes against a resourceURI

   Wildcards - * matches anything
*/
public class RouteMatcher
{
    private ArrayList<RouteSpec> _routes = null;
    private Class<? extends RequestHandler> _defaultRequestHandler = null;

    public RouteMatcher()
    {
        _routes = new ArrayList<RouteSpec>();
    }

    public List<RouteSpec> getAllRoutes()
    {
        return _routes;
    }

    public void add(String httpMethod, String path, HttpServlet servlet)
    {
        HTTPMethod httpMethodEnum = HTTPMethod.valueOf(httpMethod);
        RouteSpec routeSpec = new RouteSpec.Builder()
        .withPath(path)
        .withHTTPMethod(httpMethodEnum)
        .build();

        _routes.add(routeSpec);
    }

    public void add(String httpMethod, String path, Class<? extends RequestHandler> requestHandler)
    {
        HTTPMethod httpMethodEnum = HTTPMethod.valueOf(httpMethod);
        RouteSpec routeSpec = new RouteSpec.Builder()
        .withPath(path)
        .withHTTPMethod(httpMethodEnum)
        .withRequestHandler(requestHandler)
        .build();
        _routes.add(routeSpec);
    }

    public void setDefaultRequestHandler(Class<? extends RequestHandler> requestHandler)
    {
        _defaultRequestHandler = requestHandler;
    }

    public MatchedRoute match(HTTPMethod httpMethod, String path)
    {
        Map<String, String> routeParams = new HashMap<String, String>();
        for(RouteSpec routeSpec : _routes)
        {
            if(matches(routeSpec.getPath(), routeSpec.getHttpMethod(), httpMethod, path, routeParams))
            {
                MatchedRoute matchedRoute = new MatchedRoute(routeSpec, routeParams);
                return matchedRoute;
            }
        }

        if(_defaultRequestHandler == null)
            return null;
        //else return the default route
        RouteSpec defaultRouteSpec = new RouteSpec.Builder()
        .withPath(path)
        .withHTTPMethod(httpMethod)
        .withRequestHandler(_defaultRequestHandler)
        .build();

        MatchedRoute defaultRoute = new MatchedRoute(defaultRouteSpec, null);
        return defaultRoute;
    }

    private static boolean matches(String route, HTTPMethod routeMethod, HTTPMethod requestMethod, String resourceURI, Map<String, String> routeParams)
    {
        return matches(route, new HTTPMethod[]{routeMethod}, requestMethod, resourceURI, routeParams);
    }

    private static boolean matches(String route, HTTPMethod[] routeMethods, HTTPMethod requestMethod, String resourceURI, Map<String, String> routeParams)
    {
        boolean methodMatches = false;
        for(HTTPMethod method : routeMethods)
        {
            if(method == requestMethod)
            {
                methodMatches = true;
                break;
            }
        }

        if(!methodMatches)
            return false;

        String regexRoute = route.replaceAll(":[a-zA-Z0-9_\\\\-\\\\.~]*", "(*)");
        regexRoute = regexRoute.replaceAll("\\*", "[^/]*");

        Pattern pattern = Pattern.compile(regexRoute);
        Matcher matcher = pattern.matcher(resourceURI);

        boolean matches = matcher.matches();
        if(!matches)
            return false;

        int groupCount = matcher.groupCount();
        if(groupCount <= 0)
            return true;

        if(routeParams == null)
            return true;

        String[] parts = route.split("/");
        int index = 1;
        for(String part : parts)
        {
            if(part.startsWith(":"))
            {
                String paramVal = matcher.group(index);
                if(paramVal != null && paramVal.length() > 0)
                {
                    String urlDecoded = null;
                    try
                    {
                        urlDecoded = URLDecoder.decode(paramVal, "UTF-8");
                    }
                    catch(UnsupportedEncodingException usee)
                    {
                        //cannot happen
                        throw(new RuntimeException(usee));
                    }
                    routeParams.put(part.substring(1), urlDecoded);
                }
                index++;
            }
        }

        return true;
    }
}
