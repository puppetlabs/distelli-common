package com.distelli.monitor.impl;

public class ShuttingDownException extends IllegalStateException {
    public ShuttingDownException() {
        super("JVM is shutting down");
    }
}
