/*
  $Id: $
  @file TestDiskObjectStore.java
  @brief Contains the TestDiskObjectStore.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.objectStore.impl.disk;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.inject.Inject;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import com.distelli.persistence.PageIterator;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.*;
import com.google.inject.Guice;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class TestDiskObjectStore
{
    private static File _storageRoot = new File("target/disk-storage-root/");
    private static DiskObjectStore _diskObjectStore = new DiskObjectStore(_storageRoot);

    @BeforeClass
    public static void beforeClass()
    {

    }

    @Before
    public void beforeTest()
    {

    }

    @AfterClass
    public static void afterClass()
    {

    }

    @After
    public void afterTest()
    {

    }

    @Test
    public void testCreateDeleteBucket()
    {
        _diskObjectStore.createBucket("test-create-delete-bucket");
        File bucketDir = new File(_storageRoot, "buckets/test-create-delete-bucket");
        assertThat(bucketDir.exists(), equalTo(true));
        _diskObjectStore.deleteBucket("test-create-delete-bucket");
        assertThat(bucketDir.exists(), equalTo(false));
    }

    @Test
    public void testGetPutDeleteObject()
        throws Exception
    {
        String bucketName = "test-get-put-del-bucket";
        File bucketDir = new File(_storageRoot, "buckets/"+bucketName);
        _diskObjectStore.createBucket(bucketName);
        String content = "Hello, World";
        byte[] contentBytes = content.getBytes();

        //Put a key in a subdir
        ObjectKey key = ObjectKey
        .builder().bucket(bucketName)
        .key("foo/bar/test-key")
        .build();

        //put the object
        assertPut(bucketDir, key, contentBytes);
        //do a head on that key and ensure that its the right length
        assertHead(key, contentBytes);

        //now read that key
        assertRangeRead(key, 0L, 5L, "Hello");
        assertRangeRead(key, 2L, 8L, "llo, W");
        //now delete it
        assertDelete(bucketDir, key);

        //Put a key not in a subdir
        key = ObjectKey
        .builder().bucket(bucketName)
        .key("foo")
        .build();

        //put the key
        assertPut(bucketDir, key, contentBytes);
        //do a head on that key and ensure that its the right length
        assertHead(key, contentBytes);
        assertRangeRead(key, 0L, 5L, "Hello");
        assertRangeRead(key, 2L, 8L, "llo, W");
        //now delete it
        assertDelete(bucketDir, key);

        //Put a key not in a subdir, but ending in /
        key = ObjectKey
        .builder().bucket(bucketName)
        .key("bar/")
        .build();

        //do the put
        assertPut(bucketDir, key, contentBytes);

        //do a head on that key and ensure that its the right length
        assertHead(key, contentBytes);
        assertRangeRead(key, 0L, 5L, "Hello");
        assertRangeRead(key, 2L, 8L, "llo, W");
        //now delete it
        assertDelete(bucketDir, key);
        ObjectKey prefixKey = ObjectKey.builder().bucket(bucketName).key("/").build();
        _diskObjectStore.list(prefixKey, new PageIterator());
    }

    private void assertPut(File bucketDir, ObjectKey key, byte[] contentBytes)
    {
        ByteArrayInputStream in = new ByteArrayInputStream(contentBytes);
        _diskObjectStore.put(key, contentBytes.length, in);
        String expectedKey = key.getKey();
        if(expectedKey.endsWith("/"))
            expectedKey = expectedKey.substring(0, expectedKey.length() -1);
        File objectFile = new File(bucketDir.getAbsolutePath(), expectedKey+".obj");
        assertThat(objectFile.exists(), equalTo(true));
    }

    private void assertHead(ObjectKey key, byte[] contentBytes)
    {
        ObjectMetadata metaData = _diskObjectStore.head(key);
        assertThat(metaData, is(not(nullValue())));
        assertThat(metaData.getBucket(), equalTo(key.getBucket()));
        assertThat(metaData.getKey(), equalTo(key.getKey()));
        assertThat(metaData.getContentLength(), equalTo(new Long(contentBytes.length)));
    }

    private void assertDelete(File bucketDir, ObjectKey key)
    {
        _diskObjectStore.delete(key);
        File objectFile = new File(bucketDir.getAbsolutePath(), key.getKey()+".obj");
        assertThat(objectFile.exists(), equalTo(false));
    }

    private void assertRangeRead(ObjectKey key, Long start, Long end, String expectedValue)
        throws IOException
    {
        final int expectedTotalRead = (int)(end-start);
        _diskObjectStore.get(key,
                             (meta, inputStream) -> {
                                 byte[] buf = new byte[1024];
                                 int bytesRead = inputStream.read(buf, 0, buf.length);
                                 byte[] data = Arrays.copyOf(buf, bytesRead);
                                 assertThat(data, is(not(nullValue())));
                                 assertThat(bytesRead, equalTo(expectedTotalRead));
                                 assertThat(new String(data), equalTo(expectedValue));
                                 return bytesRead;
                             }, start, end);
    }
}
