package com.distelli.objectStore;

public class StreamCorruptedException extends RuntimeException {
    public StreamCorruptedException() {}
    public StreamCorruptedException(String message) {
        super(message);
    }
    public StreamCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
