package com.distelli.monitor.impl;

public class LostLockException extends IllegalStateException {
    public LostLockException(String entity) {
        super("Lost lock for "+entity);
    }
}
