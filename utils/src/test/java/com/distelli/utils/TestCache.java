package com.distelli.utils;

import org.junit.Assert;
import org.junit.Test;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class TestCache {
    @Test
    public void testComputeOnce() throws Exception {
        Semaphore cacheGetSemaphore = new Semaphore(0);
        Cache<String, String> cache = Cache.<String, String>newBuilder()
            .maximumSize(10)
            .expireAfterWrite(1, TimeUnit.MILLISECONDS)
            .build((key) -> {
                    try {
                        cacheGetSemaphore.acquire();
                    } catch ( InterruptedException ex ) {
                        throw new RuntimeException(ex);
                    }
                    return key;
                });
        Future<String> future1 = ForkJoinPool.commonPool()
            .submit(() -> cache.get("one"));
        Future<String> future2 = ForkJoinPool.commonPool()
            .submit(() -> cache.get("one"));
        Future<String> future3 = ForkJoinPool.commonPool()
            .submit(() -> cache.get("one"));

        Thread.sleep(100);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());

        // Release a SINGLE permit
        cacheGetSemaphore.release();

        // If compute was called multiple times, then the semaphore
        // would block.
        assertEquals("one", future1.get(1, TimeUnit.SECONDS));
        assertEquals("one", future2.get(1, TimeUnit.SECONDS));
        assertEquals("one", future3.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void testEviction() throws Exception {
        AtomicInteger computes = new AtomicInteger();
        Cache<String, String> cache = Cache.<String, String>newBuilder()
            .maximumSize(10)
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .build((key) -> {
                    if ( "one".equals(key) ) computes.getAndIncrement();
                    return key;
                });
        cache.get("a");
        cache.get("b");
        assertEquals(cache.size(), 2);

        int i=0;
        long start = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
        for (; computes.get() <= 1; i++) {
            cache.get("one");
        }
        long duration = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS) - start;

        // "a" and "b" should have been evicted
        assertEquals(cache.size(), 1);

        assertThat(i, greaterThan(2));
        assertThat(duration+1, greaterThan(100L));

        System.out.println("100ms expiration, get() count="+i+" actual duration="+duration+"ms");
    }
}
