package com.distelli.utils;

import java.util.concurrent.Callable;

public interface VoidCallable {
    public void call() throws Exception;
    public default Callable<Void> toCallable() {
        return () -> {
            this.call();
            return null;
        };
    }
}
