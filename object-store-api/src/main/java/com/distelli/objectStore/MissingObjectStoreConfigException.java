package com.distelli.objectStore;

public class MissingObjectStoreConfigException extends RuntimeException {
    public MissingObjectStoreConfigException(String msg) {
        super(msg);
    }
}
