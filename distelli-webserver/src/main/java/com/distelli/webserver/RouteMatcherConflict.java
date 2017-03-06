package com.distelli.webserver;

public class RouteMatcherConflict extends RuntimeException {
    public RouteMatcherConflict(String msg) {
        super(msg);
    }
}
