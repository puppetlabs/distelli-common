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
public class RouteMatcher extends GenericRouteMatcher<Class<? extends RequestHandler>>
{
    @Override
    public MatchedRoute match(HTTPMethod httpMethod, String path) {
        return new MatchedRoute(match(httpMethod, path));
    }
}
