package com.distelli.objectStore;

import java.io.InputStream;
import java.io.IOException;

public interface ObjectReader<T> {
    public T read(ObjectMetadata objectMetadata, InputStream inputStream) throws IOException;
}
