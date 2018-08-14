package com.distelli.objectStore;

import com.distelli.cred.CredProvider;
import com.distelli.persistence.PageIterator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.AccessControlException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityNotFoundException;

public interface ObjectStore {
    public interface Builder {
        public Builder withDiskStorageRoot(File dir);
        public Builder withEndpoint(URI endpoint);
        public Builder withCredProvider(CredProvider credProvider);
        public Builder withProxy(URI proxy);
        public Builder withObjectStoreType(ObjectStoreType objectStoreType);
        // S3 specific parameters:
        public Builder withForceV4Signature(Boolean forceV4);
        public Builder withServerSideEncryption(Boolean serverSideEncryption);
        public ObjectStore build();
    }
    public interface Factory {
        public Builder create();
        /**
         * Create an `ObjectKey` with the default bucket and a possible
         * key prefix prepended.
         */
        public default ObjectKey createKey(String key) {
            throw new MissingObjectStoreConfigException("No ObjectStore configuration is defined.");
        }
    }

    public void createBucket(String bucketName) throws AccessControlException;
    public void deleteBucket(String bucketName) throws AccessControlException;

    public void put(ObjectKey objectKey, long contentLength, InputStream in) throws EntityNotFoundException, AccessControlException;
    public void put(ObjectKey objectKey, File in) throws EntityNotFoundException, IOException, AccessControlException;
    public void put(ObjectKey objectKey, byte[] in) throws EntityNotFoundException, AccessControlException;

    // Returns null if entity does not exist.
    public ObjectMetadata head(ObjectKey objectKey) throws AccessControlException;

    /**
     * Get object with objectKey from offset start to end, streaming it into the objectReader.
     *
     * @param <T> - the return type of the object reader.
     * @param objectKey - the key of the object to fetch.
     * @param objectReader - the reader that will handle the results.
     * @param start - the offset start (or null to start at the beginning).
     * @param end - the offset end (or null to start at the end).
     *
     * @throws EntityNotFoundException if the key does not exist.
     * @throws IOException if objectReader throws this.
     * @throws AccessControlException if access to this key is denied.
     * @throws StreamCorruptedException after reading the entire stream if
     *         the final checksum does not match the expected checksum. This
     *         check may be disabled in various scenarios depending on the
     *         underlying implementation.
     * @return Returns the result of ObjectReader
     */
    public <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader, Long start, Long end)
        throws EntityNotFoundException, IOException, AccessControlException, StreamCorruptedException;

    public default <T> T get(ObjectKey objectKey, ObjectReader<T> objectReader)
        throws EntityNotFoundException, IOException, AccessControlException, StreamCorruptedException
    {
        return get(objectKey, objectReader, null, null);
    }
    public void get(ObjectKey objectKey, File file)
        throws EntityNotFoundException, IOException, AccessControlException, StreamCorruptedException;
    public byte[] get(ObjectKey objectKey)
        throws EntityNotFoundException, IOException, AccessControlException, StreamCorruptedException;

    public List<ObjectKey> list(ObjectKey objectKey, PageIterator iterator) throws EntityNotFoundException, AccessControlException;
    public void delete(ObjectKey objectKey) throws AccessControlException;
    public URI createSignedGet(ObjectKey objectKey, long timeout, TimeUnit unit);

    /**
     * Create a new multipart PUT.
     *
     * @param objectKey is the object key we want to create.
     *
     * @return the part key which contains the new upload id.
     */
    public ObjectPartKey newMultipartPut(ObjectKey objectKey)
        throws EntityNotFoundException, AccessControlException;

    /**
     * PUT an individual part.
     *
     * @param partKey as returned from newMultipartPut().
     *
     * @param partNum used to sequence the parts uploaded.
     *
     * @param in the content to be uploaded.
     *
     * @return the "part id" that should be used in completePut().
     */
    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, long contentLength, InputStream in)
        throws EntityNotFoundException, AccessControlException;

    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, File in)
        throws EntityNotFoundException, IOException, AccessControlException;

    public ObjectPartId multipartPut(ObjectPartKey partKey, int partNum, byte[] in)
        throws EntityNotFoundException, AccessControlException;

    /**
     * Abort a multipart put.
     *
     * @param partKey as returned from newMultipartPut().
     */
    public void abortPut(ObjectPartKey partKey)
        throws EntityNotFoundException;

    /**
     * Complete a multipart put.
     *
     * @param partKey as returned from newMultipartPut().
     *
     * @param partKeys as returned by calls to multipartPut().
     *
     * @throws ChunkToSmallException if an individual part is &lt; 5MB (unless it is the last chunk).
     */
    public void completePut(ObjectPartKey partKey, List<ObjectPartId> partKeys)
        throws EntityNotFoundException, ChunkToSmallException;
}
