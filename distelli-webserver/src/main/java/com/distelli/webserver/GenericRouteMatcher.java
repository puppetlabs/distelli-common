package com.distelli.webserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
   The route matcher matches HTTP routes against a resourceURI

   Wildcards - * matches anything
*/
public class GenericRouteMatcher<T>
{
    private static final Logger LOG = LoggerFactory.getLogger(GenericRouteMatcher.class);
    private static Pattern NAMED_COMP_REGEX = Pattern.compile(":[a-zA-Z0-9_\\\\-\\\\.~]*|\\*");
    // Path components are separated by /, so use that as the special key
    // to denote that all path components match:
    private static String GLOB_KEY = "/";
    private static class Node<T> {
        protected Map<String, Node<T>> paths;
        // Leaf:
        protected GenericRouteSpec<T> routeSpec;
        protected List<String> paramNames;
    }

    private Node<T> root = new Node<>();
    private T defaultValue;

    public List<GenericRouteSpec<T>> getAllRoutes() {
        List<GenericRouteSpec<T>> result = new ArrayList<>();
        List<Node<T>> stack = new ArrayList<>();
        stack.add(root);
        while ( ! stack.isEmpty() ) {
            Node<T> node = stack.remove(stack.size()-1);
            if ( null != node.routeSpec ) {
                result.add(node.routeSpec);
            }
            if ( null == node.paths ) continue;
            stack.addAll(node.paths.values());
        }
        return result;
    }

    public void add(GenericRouteSpec<T> routeSpec) {
        add(root, toComponents(routeSpec.getHttpMethod(), routeSpec.getPath()), new ArrayList<String>(), routeSpec);
    }

    public void add(String httpMethod, String path, T value) {

        HTTPMethod httpMethodEnum = HTTPMethod.valueOf(httpMethod);
        GenericRouteSpec<T> routeSpec = GenericRouteSpec.<T>builder()
            .withPath(path)
            .withHTTPMethod(httpMethodEnum)
            .withValue(value)
            .build();
        add(routeSpec);
    }

    public void setDefault(T defaultVal)
    {
        defaultValue = defaultVal;
    }

    public GenericMatchedRoute<T> match(HTTPMethod httpMethod, String path) {
        GenericMatchedRoute<T> result =
            match(root, toComponents(httpMethod, path), new ArrayList<>());
        if ( null != result ) {
            if ( LOG.isDebugEnabled() ) LOG.debug("Route matched: "+result);
            return result;
        }
        if ( null == defaultValue ) return null;
        return new GenericMatchedRoute<T>(
            GenericRouteSpec.<T>builder()
            .withPath(path)
            .withHTTPMethod(httpMethod)
            .withValue(defaultValue)
            .build(),
            Collections.emptyMap());
    }


    private GenericMatchedRoute<T> match(Node<T> node, List<String> components, List<String> paramValues) {
        if ( components.isEmpty() ) {
            // Leaf:
            if ( null == node.routeSpec ) return null;
            Map<String, String> params = new LinkedHashMap<>();
            for ( int i=0; i < paramValues.size(); i++ ) {
                params.put(node.paramNames.get(i), paramValues.get(i));
            }
            return new GenericMatchedRoute<T>(
                node.routeSpec,
                Collections.unmodifiableMap(params));
        }
        if ( null == node.paths ) {
            return null;
        }
        Node<T> child = node.paths.get(components.get(0));
        GenericMatchedRoute<T> result;
        if ( null != child ) {
            result = match(child, components.subList(1, components.size()), paramValues);
            if ( null != result ) {
                return result;
            }
        }
        child = node.paths.get(GLOB_KEY);
        if ( null == child ) return null;
        paramValues.add(components.get(0));
        result = match(child, components.subList(1, components.size()), paramValues);
        if ( null != result ) {
            return result;
        }
        paramValues.remove(paramValues.size()-1);
        return null;
    }

    private List<String> toComponents(HTTPMethod httpMethod, String path) {
        // Trim off beginning slash:
        if ( path.startsWith("/") ) {
            path = path.substring(1);
        }
        // Trim off any trailing slashes:
        path = path.replaceAll("/+$", "");
        List<String> components = new ArrayList<>();
        components.add(httpMethod.toString());
        components.addAll(Arrays.asList(path.split("/")));
        return components;
    }

    private void add(Node<T> node, List<String> components, List<String> paramNames, GenericRouteSpec<T> routeSpec) {
        if ( components.isEmpty() ) {
            // Leaf:
            if ( null != node.routeSpec ) {
                throw new RouteMatcherConflict(
                    routeSpec.getHttpMethod() + " " + routeSpec.getPath() +
                    " " + routeSpec.getValue() + " conflicts with " + node.routeSpec +
                    " paramNames="+node.paramNames);
            }
            node.routeSpec = routeSpec;
            node.paramNames = paramNames;
            return;
        }
        if ( null == node.paths ) {
            node.paths = new LinkedHashMap<>();
        }
        String component = components.remove(0);
        if ( NAMED_COMP_REGEX.matcher(component).matches() ) {
            paramNames.add(component.substring(1));
            component = GLOB_KEY;
        }
        Node<T> child = node.paths.get(component);
        if ( null == child ) {
            child = new Node<>();
            node.paths.put(component, child);
        }
        // Tail recursion:
        add(child, components, paramNames, routeSpec);
    }
}
