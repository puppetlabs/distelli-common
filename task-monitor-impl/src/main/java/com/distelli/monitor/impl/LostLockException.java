package com.distelli.monitor.impl;

public class LostLockException extends IllegalStateException {
    public LostLockException(long taskId) {
        super("Lost lock for taskId="+taskId);
    }
}
