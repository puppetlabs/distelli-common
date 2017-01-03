package com.distelli.objectStore.impl;

import com.distelli.cred.CredProvider;
import com.distelli.objectStore.*;
import com.distelli.persistence.PageIterator;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.AccessControlException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityNotFoundException;

public abstract class AbstractObjectStore implements ObjectStore {
    @Override
    abstract public void createBucket(String bucketName);

    @Override
    abstract public void deleteBucket(String bucketName);

    @Override
    abstract public void put(ObjectKey objectKey, long contentLength, InputStream in);

    // Returns null if entity does not exist.
    @Override
    abstract public ObjectMetadata head(ObjectKey objectKey);

    @Override
    abstract public <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader, Long start, Long end) throws EntityNotFoundException, IOException;

    @Override
    abstract public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator);

    @Override
    abstract public void delete(ObjectKey objectKey) throws EntityNotFoundException;

    @Override
    abstract public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit) throws EntityNotFoundException;

    @Override
    abstract public ObjectPartKey newMultipartPut(ObjectKey objectKey);


    @Override
    abstract public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in);

    @Override
    abstract public void abortPut(ObjectPartKey partKey);

    @Override
    abstract public void completePut(ObjectPartKey partKey, List<ObjectPartId> partKeys);

    @Override
    public void put(ObjectKey objectKey, File in) throws IOException {
        put(objectKey, in.length(), new FileInputStream(in));
    }

    @Override
    public void put(ObjectKey objectKey, byte[] in) {
        put(objectKey, in.length, new ByteArrayInputStream(in));
    }

    @Override
    public byte[] get(ObjectKey objectKey) throws IOException, EntityNotFoundException {
        return get(objectKey, (meta, is) -> {
                byte[] buff = new byte[Math.toIntExact(meta.getContentLength())];
                new DataInputStream(is).readFully(buff);
                return buff;
            });
    }

    @Override
    public void get(ObjectKey objectKey, File file) throws EntityNotFoundException, IOException {
        get(objectKey, (meta, is) -> Files.copy(is, file.toPath()));
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, File in) throws IOException {
        return multipartPut(partKey, partNum, in.length(), new FileInputStream(in));
    }

    @Override
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, byte[] in) {
        return multipartPut(partKey, partNum, in.length, new ByteArrayInputStream(in));
    }

}
