package com.distelli.utils;

import java.util.function.Function;
import java.util.concurrent.TimeUnit;

/**
 * Usage:
 * <pre>
 *   public MyValue createValueFor(MyKey key);
 *
 *   Cache&lt;MyKey, MyValue&gt; cache = Cache.newBuilder()
 *       .maximumSize(100)
 *       .expireAfterWrite(1, TimeUnit.MINUTE)
 *       .build((key) -&gt; createValueFor(key));
 *
 *   // Computes value on first hit. If multiple threads
 *   // request the same key, they will all wait for the
 *   // first compute to complete.
 *   cache.get(someKey);
 * </pre>
 */
public interface Cache<K, V> {
    public interface Builder<K, V> {
        /**
         * @param maxTimeToLive is the maximum time a cache entry is allowed
         *    to be returned from any get/getIfPresent() call.
         *
         * @param unit is the time units for this.
         *
         * @return this
         */
        public Builder<K, V> expireAfterWrite(long maxTimeToLive, TimeUnit unit);

        /**
         * @param size is the maximum cache entries allowed.
         *
         * @return this
         */
        public Builder<K, V> maximumSize(int size);

        /**
         * @param compute is the function used to compute an element that
         *     does not exist in the cache or that was expired.
         *
         * @return a new cache.
         */
        public Cache<K, V> build(Function<K, V> compute);
    }

    public static <K, V> Builder<K, V> newBuilder() {
        return new CacheImpl.Builder<K, V>();
    }

    /**
     * @param key to lookup.
     *
     * @return null if key is not already in cache (it might be
     *     in the middle of computing the value)
     */
    public V getIfPresent(K key);

    /**
     * @param key to lookup.
     *
     * @return the value associated with the key or compute the value if
     *    it is not already in the cache.
     *
     * @throws CacheComputeException if the attempt to compute
     *     the cache key failed. See @{RuntimeException.getCause()}
     *     to obtain the root cause.
     */
    public V get(K key) throws CacheComputeException;

    /**
     * @param key to replace.
     *
     * @param value to replace it with.
     */
    public void put(K key, V value);

    /**
     * @param key to remove.
     */
    public void remove(K key);

    /**
     * Removes cache entries until the cache is no larget than max.
     * NOTE: The max size of the cache is NOT changed.
     *
     * @param max is the max cache size allowed.
     */
    public void resize(int max);

    /**
     * @return the number of elements currently in the cache.
     */
    public int size();
}
