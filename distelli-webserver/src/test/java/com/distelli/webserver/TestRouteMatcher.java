package com.distelli.webserver;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestRouteMatcher {
    @Test(expected = RouteMatcherConflict.class)
    public void testConflict() {
        GenericRouteMatcher<String> routeMatcher = new GenericRouteMatcher<>();
        routeMatcher.add("GET", "/:name/bar", "A");
        routeMatcher.add("GET", ":buz/bar", "B");
    }

    @Test
    public void testIgnoreExtraSlashes() {
        GenericRouteMatcher<String> routeMatcher = new GenericRouteMatcher<>();
        routeMatcher.add("GET", "/:name/bar", "A");
        assertEquals("A", match(routeMatcher, "GET", "/foo/bar/").getValue());
    }

    @Test
    public void testMostSpecific() {
        for ( int i=1; i <= 8; i++ ) {
            GenericRouteMatcher<Integer> routeMatcher = new GenericRouteMatcher<>();
            routeMatcher.add("GET", "/:foo/:bar/:baz", 8); // least specific
            if ( i < 8 ) routeMatcher.add("GET", "/:foo/:bar/baz", 7);
            if ( i < 7 ) routeMatcher.add("GET", "/:foo/bar/:baz", 6);
            if ( i < 6 ) routeMatcher.add("GET", "/:foo/bar/baz", 5);
            if ( i < 5 ) routeMatcher.add("GET", "/foo/:bar/:baz", 4);
            if ( i < 4 ) routeMatcher.add("GET", "/foo/:bar/baz", 3);
            if ( i < 3 ) routeMatcher.add("GET", "/foo/bar/:baz", 2);
            if ( i < 2 ) routeMatcher.add("GET", "/foo/bar/baz", 1); // most specific
            assertEquals(i, match(routeMatcher, "GET","/foo/bar/baz").getValue().intValue());
        }
    }

    protected <T> GenericMatchedRoute<T> match(GenericRouteMatcher<T> routeMatcher, String httpMethod, String path) {
        return routeMatcher.match(HTTPMethod.valueOf(httpMethod), path);
    }
}
