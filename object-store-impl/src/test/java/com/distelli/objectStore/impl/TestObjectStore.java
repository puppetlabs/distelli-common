package com.distelli.objectStore.impl;

import com.google.inject.AbstractModule;
import com.distelli.objectStore.*;
import com.distelli.cred.CredPair;
import com.distelli.persistence.PageIterator;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.name.Names;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TestObjectStore {
    private static Injector INJECTOR = Guice.createInjector(
        new ObjectStoreModule());

    @Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { ObjectStoreType.S3 },
                { ObjectStoreType.DISK },
                { ObjectStoreType.ARTIFACTORY }
            });
    }

    @Inject
    private ObjectStore.Factory objectStoreFactory;

    private ObjectStore objectStore;
    private ObjectStoreType osProvider;
    private String bucketName;

    public TestObjectStore(ObjectStoreType osProvider) {
        this.osProvider = osProvider;
    }

    @Before
    public void beforeTest() {
        INJECTOR.createChildInjector(
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ObjectStore.Factory.class)
                        .toProvider(new ObjectStoreFactoryProvider(osProvider));
                }
            })
            .injectMembers(this);

        objectStore = objectStoreFactory.create().build();
        bucketName = System.getenv("S3_BUCKET");
        if ( null == bucketName ) {
            throw new IllegalStateException("Please set the S3_BUCKET environment variable to the test S3 bucket");
        }
        // Make sure the bucket exists...
        objectStore.createBucket(bucketName);
    }

    @Test
    public void testCreateSignedGetUrl()
        throws Exception
    {
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key("agent.txt")
            .build();

        long seconds = 7L;
        URI url = objectStore.createSignedGet(key, seconds, TimeUnit.SECONDS);

        System.out.println("SignedS3URL="+url);
        /**
TODO: Use http-client to do a request:
        GenericHttpClient httpClient = GenericHttpClient.create().build();

        int statusBefore = httpClient.GET().withPath(url.toString())
            .execute((response, inputStream) -> response.getHttpStatusCode());
        assertThat(statusBefore, equalTo(200));

        Thread.sleep(seconds*1000);
        int statusAfter = 0;
        try {
            statusAfter = httpClient.GET().withPath(url.toString())
                .execute((response, inputStream) -> response.getHttpStatusCode());
            //Should not have reached here
            fail("Should have thrown a ClientError");
        } catch(ClientError ce) {
            //exception is expected
            statusAfter = ce.getStatusCode();
            assertThat(403, equalTo(ce.getStatusCode()));
        }
        assertThat(403, equalTo(statusAfter));
        */
    }

    @Test
    public void testLargeInput() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        int fiveMB = 5*1024*1024;
        AtomicInteger remaining = new AtomicInteger(fiveMB);
        InputStream putIS = new InputStream() {
                public int read() {
                    if ( remaining.decrementAndGet() < 0 ) return -1;
                    return 64;
                }
            };
        objectStore.put(key, remaining.get(), putIS);
        try {
            // No-op read:
            objectStore.get(
                key,
                (meta, in) -> {
                    in.read();
                    return null;
                });
            // Full read:
            AtomicInteger got = new AtomicInteger(0);
            objectStore.get(
                key,
                (meta, in) -> {
                    while ( in.read() > 0 ) {
                        got.incrementAndGet();
                    }
                    in.close();
                    return null;
                });
            assertEquals(got.get(), fiveMB);
        } finally {
            objectStore.delete(key);
        }

    }

    @Test(expected=AccessControlException.class)
    public void testAccessDeniedPut() throws Exception {
        // Creds are ignored, so we can't do this test:
        if ( ObjectStoreType.DISK == osProvider ) throw new AccessControlException("");
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();

        badCredProvider().put(key, 0, new ByteArrayInputStream(new byte[0]));
    }

    @Test
    public void testBucketCreate() throws Exception {
        // buckets must not begin with numbers:
        String bucketName = "a"+UUID.randomUUID().toString();
        try {
            objectStore.createBucket(bucketName);
        } finally {
            objectStore.deleteBucket(bucketName);
        }
    }

    public void testBadBucketPut() throws Exception {
        try {
            ObjectKey key = ObjectKey.builder()
                .bucket(UUID.randomUUID().toString())
                .key("something")
                .build();
            objectStore.put(key, 0, new ByteArrayInputStream(new byte[0]));
        } catch ( AccessControlException ex ) {
            // rather strange behavior... we get a 403 :(.
            assertEquals(osProvider, ObjectStoreType.ARTIFACTORY);
            return;
        } catch ( EntityNotFoundException ex ) {
            return;
        }
        fail("Expected AccessControlException or EntityNotFoundException exception");
    }

    @Test(expected=AccessControlException.class)
    public void testAccessDeniedGet() throws Exception {
        // Creds are ignored, so we can't do this test:
        if ( ObjectStoreType.DISK == osProvider ) throw new AccessControlException("");
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        badCredProvider().get(key, (meta, in) -> null);
    }

    @Test(expected=EntityNotFoundException.class)
    public void testBadBucketGet() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(UUID.randomUUID().toString())
            .key("something")
            .build();
        objectStore.get(key, (meta, in) -> null);
    }

    @Test(expected=AccessControlException.class)
    public void testAccessDeniedHead() throws Exception {
        // Creds are ignored, so we can't do this test:
        if ( ObjectStoreType.DISK == osProvider ) throw new AccessControlException("");
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        badCredProvider().head(key);
    }

    // Do not want to throw...
    @Test
    public void testBadBucketHead() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(UUID.randomUUID().toString())
            .key("something")
            .build();
        assertNull(objectStore.head(key));
    }

    @Test(expected=AccessControlException.class)
    public void testAccessDeniedList() throws Exception {
        // Creds are ignored, so we can't do this test:
        if ( ObjectStoreType.DISK == osProvider ) throw new AccessControlException("");
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        badCredProvider().list(key, new PageIterator());
    }

    @Test(expected=EntityNotFoundException.class)
    public void testBadBucketList() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(UUID.randomUUID().toString())
            .key("something")
            .build();
        objectStore.list(key, new PageIterator());
    }

    @Test(expected=AccessControlException.class)
    public void testAccessDeniedDelete() throws Exception {
        // Creds are ignored, so we can't do this test:
        if ( ObjectStoreType.DISK == osProvider ) throw new AccessControlException("");
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        badCredProvider().delete(key);
    }

    public void testBadBucketDelete() throws Exception {
        try {
            ObjectKey key = ObjectKey.builder()
                .bucket(UUID.randomUUID().toString())
                .key("something")
                .build();
            objectStore.delete(key);
        } catch ( AccessControlException ex ) {
            // rather strange behavior... we get a 403 :(.
            assertEquals(osProvider, ObjectStoreType.ARTIFACTORY);
            return;
        } catch ( EntityNotFoundException ex ) {
            return;
        }
        fail("Expected AccessControlException or EntityNotFoundException exception");
    }

    // Do not want to throw...
    @Test @Ignore
    public void testBadKeyDelete() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key(UUID.randomUUID().toString())
            .build();
        objectStore.delete(key);
    }

    private ObjectStore badCredProvider() {
        CredPair credPair = new CredPair()
            .withKeyId("doesnotexist")
            .withSecret("doesnotexist");
        return objectStoreFactory.create()
            .withCredProvider(() -> credPair)
            .build();
    }

    @Test
    public void testList() throws Exception {
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key("")
            .build();
        byte[] empty = new byte[0];
        int max = 25;
        try {
            Set<String> expect = new HashSet<>();
            key.setKey("test-list-foo");
            //expect.add(key.getKey());
            objectStore.put(key, empty);
            key.setKey("test-xxx");
            objectStore.put(key, empty);
            for ( int i=0; i < max; i++ ) {
                key.setKey(String.format("test-list/test-%03d", i));
                expect.add(key.getKey());
                objectStore.put(key, empty);
            }
            key.setKey("test-list/");
            Set<String> seen = new HashSet<>();
            for ( PageIterator it : new PageIterator().pageSize(5) ) {
                for ( ObjectKey meta : objectStore.list(key, it) ) {
                    assertEquals(meta.getBucket(), key.getBucket());
                    seen.add(meta.getKey());
                }
            }
            assertEquals(expect, seen);
        } finally {
            for ( int i=0; i < max; i++ ) {
                key.setKey(String.format("test-list/test-%03d", i));
                objectStore.delete(key);
            }
            key.setKey("test-list-foo");
            objectStore.delete(key);
        }
    }

    @Test
    public void testPutGetDelete() throws Exception {
        long time = System.currentTimeMillis();
        final String data = "TestDataForTheKey-"+time;
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key("TestKey-"+time)
            .build();

        //put the data
        objectStore.put(key, data.getBytes(UTF_8));
        //now get the data
        byte[] got = objectStore.get(key);
        assertThat(data, equalTo(new String(got, UTF_8)));
        //now get the object details
        ObjectMetadata meta = objectStore.head(key);
        assertNotNull(meta);
        assertEquals(got.length, Math.toIntExact(meta.getContentLength()));
        assertEquals(key.getKey(), meta.getKey());
        assertEquals(key.getBucket(), meta.getBucket());

        //delete the key
        objectStore.delete(key);

        Thread.sleep(2000);
        assertNull(objectStore.head(key));
        boolean failed = false;
        try {
            got = objectStore.get(key);
            fail("Expected EntityNotFoundException exception, got="+Arrays.toString(got));
        } catch ( EntityNotFoundException ex ) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void testMulitpartPut() throws Exception {
        if ( osProvider == ObjectStoreType.ARTIFACTORY ) {
            System.err.println("TODO: Artifactory does NOT support multi-part PUTs");
            return;
        }

        long time = System.currentTimeMillis();
        ObjectKey key = ObjectKey.builder()
            .bucket(bucketName)
            .key("TestKey-"+time)
            .build();

        byte[] chunk = new byte[5*1048576];

        // start put:
        ObjectPartKey partKey = null;
        try {
            partKey = objectStore.newMultipartPut(key);
            List<ObjectPartId> partIds = new ArrayList<>();
            Arrays.fill(chunk, (byte)'a');
            partIds.add(objectStore.multipartPut(partKey, 1, chunk));
            Arrays.fill(chunk, (byte)'b');
            partIds.add(objectStore.multipartPut(partKey, 2, chunk));
            partIds.add(objectStore.multipartPut(partKey, 3, new byte[]{(byte)'c'}));

            objectStore.completePut(partKey, partIds);

            partKey = null;

            //now get the data
            objectStore.get(key, (meta, is) -> {
                    for ( int i=0; i < chunk.length; i++ ) {
                        assertEquals(is.read(), (int)'a');
                    }
                    for ( int i=0; i < chunk.length; i++ ) {
                        assertEquals(is.read(), (int)'b');
                    }
                    assertEquals(is.read(), (int)'c');
                    assertEquals(is.read(), -1);
                    return null;
                });

            //now get the object details
            ObjectMetadata meta = objectStore.head(key);
            assertEquals(2*chunk.length+1, Math.toIntExact(meta.getContentLength()));
            assertEquals(key.getKey(), meta.getKey());
            assertEquals(key.getBucket(), meta.getBucket());
        } finally {
            // Maybe abort the multi-part request.
            if ( null != partKey ) objectStore.abortPut(partKey);
            //delete the key
            objectStore.delete(key);
        }
    }
}
