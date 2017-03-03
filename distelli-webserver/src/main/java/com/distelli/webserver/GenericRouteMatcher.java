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
public class GenericRouteMatcher<T>
{
    private ArrayList<GenericRouteSpec<T>> _routes = null;
    private T _default = null;

    public GenericRouteMatcher()
    {
        _routes = new ArrayList<GenericRouteSpec<T>>();
    }

    public List<GenericRouteSpec<T>> getAllRoutes()
    {
        return _routes;
    }

    public void add(String httpMethod, String path, HttpServlet servlet)
    {
        HTTPMethod httpMethodEnum = HTTPMethod.valueOf(httpMethod);
        GenericRouteSpec<T> routeSpec = GenericRouteSpec.<T>builder()
            .withPath(path)
            .withHTTPMethod(httpMethodEnum)
            .build();

        _routes.add(routeSpec);
    }

    public void add(String httpMethod, String path, T value)
    {
        HTTPMethod httpMethodEnum = HTTPMethod.valueOf(httpMethod);
        GenericRouteSpec<T> routeSpec = GenericRouteSpec.<T>builder()
            .withPath(path)
            .withHTTPMethod(httpMethodEnum)
            .withValue(value)
            .build();
        _routes.add(routeSpec);
    }

    public void setDefault(T defaultVal)
    {
        _default = defaultVal;
    }

    public GenericMatchedRoute<T> match(HTTPMethod httpMethod, String path)
    {
        Map<String, String> routeParams = new HashMap<String, String>();
        for(GenericRouteSpec<T> routeSpec : _routes)
        {
            if(matches(routeSpec.getPath(), routeSpec.getHttpMethod(), httpMethod, path, routeParams))
            {
                GenericMatchedRoute<T> matchedRoute = new GenericMatchedRoute<T>(routeSpec, routeParams);
                return matchedRoute;
            }
        }

        if(_default == null)
            return null;
        //else return the default route
        GenericRouteSpec<T> defaultRouteSpec = GenericRouteSpec.<T>builder()
            .withPath(path)
            .withHTTPMethod(httpMethod)
            .withValue(_default)
            .build();

        GenericMatchedRoute<T> defaultRoute = new GenericMatchedRoute<T>(defaultRouteSpec, null);
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
