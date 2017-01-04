package com.distelli.objectStore;

public class ChunkToSmallException extends RuntimeException {
    public ChunkToSmallException() {
    }
    public ChunkToSmallException(String message, Throwable cause) {
        super(message, cause);
    }
    public ChunkToSmallException(String message) {
        super(message);
    }
    public ChunkToSmallException(Throwable cause) {
        super(cause);
    }
}
