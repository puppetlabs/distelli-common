package com.distelli.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Very simplicistic cache. An entry with a high hit-rate does NOT
 * stay in the cache for a longer period of time.
 */
class CacheImpl<K, V> implements Cache<K, V> {
    protected static class Builder<K, V> implements Cache.Builder<K, V> {
        private long _ttl;
        private int _max;

        @Override
        public Builder<K, V> expireAfterWrite(long maxTimeToLive, TimeUnit unit) {
            _ttl = TimeUnit.NANOSECONDS.convert(maxTimeToLive, unit);
            return this;
        }

        @Override
        public Builder<K, V> maximumSize(int size) {
            _max = size;
            return this;
        }

        @Override
        public Cache<K, V> build(Function<K, V> compute) {
            return new CacheImpl<K, V>(compute, _ttl, _max);
        }
    }

    /**
     * Avoid holding on to a lock for more than MAX_REMOVED iterations.
     */
    private static final int MAX_REMOVED = 10;
    private static final Object NULL = new Object();
    private static final Object FAILED = new Object();

    private static class Value<V> {
        private Throwable _error;
        // TODO: Store _value as a SoftReference!
        private Object _value;
        private long _expiresAt;

        public synchronized void setFailed(Throwable ex) {
            _error = ex;
            notifyAll();
        }

        public synchronized void setValue(V value, long expiresAt) {
            // First set "wins"
            if ( null != _value || null != _error ) return;
            _value = ( null == value ) ? NULL : value;
            _expiresAt = expiresAt;
            notifyAll();
        }

        public synchronized boolean isExpired(long nanoTime) {
            if ( null == _value ) return false;  // Primordial.
            if ( null != _error ) return true;   // Failed.
            return nanoTime > _expiresAt;
        }

        public synchronized V getValueIfPresent() {
            if ( null == _value ) return null;
            return NULL == _value ? null : (V)_value;
        }

        public synchronized V getValue() {
            while ( true ) {
                if ( null != _error ) throw new CacheComputeException(_error.getMessage(), _error);
                if ( null != _value ) return NULL == _value ? null : (V)_value;
                try {
                    wait();
                } catch ( InterruptedException ex ) {
                    throw new CacheComputeException(ex.getMessage(), ex);
                }
            }
        }
    }

    // @GuardedBy(this)
    private Map<K, Value<V>> _map = new LinkedHashMap<K, Value<V>>();

    // Read-only:
    private long _ttl;
    private int _max;
    private Function<K, V> _compute;

    private CacheImpl(Function<K, V> compute, long ttl, int max) {
        if ( null == compute ) {
            throw new IllegalArgumentException("Expected compute function to be non-null");
        }
        if ( ttl < 1 ) {
            throw new IllegalArgumentException("Expected expireAfterWrite to be greater than zero");
        }
        if ( max < 1 ) {
            throw new IllegalArgumentException("Expected maximumSize to be greater than zero");
        }
        _compute = compute;
        _ttl = ttl;
        _max = max;
    }

    private synchronized void removeExpired(long nanoTime) {
        Iterator<Value<V>> it = _map.values().iterator();
        int size = _map.size();
        for ( int i=0; i < MAX_REMOVED && it.hasNext(); i++ ) {
            if ( it.next().isExpired(nanoTime) || size > _max ) {
                it.remove();
                size--;
            } else {
                break;
            }
        }
    }

    @Override
    public synchronized V getIfPresent(K key) {
        Value<V> value = _map.get(key);
        if ( null == value ) return null;
        if ( value.isExpired(System.nanoTime()) ) {
            _map.remove(key);
            return null;
        }
        return value.getValueIfPresent();
    }

    @Override
    public V get(K key) {
        Value<V> value;
        boolean populate = false;
        synchronized ( this ) {
            value = _map.get(key);
            if ( null == value || value.isExpired(System.nanoTime()) ) {
                populate = true;
                value = new Value<>();
                _map.put(key, value);
            }
        }
        if ( ! populate ) {
            return value.getValue();
        }
        V rawValue;
        try {
            rawValue = _compute.apply(key);
        } catch ( Throwable ex ) {
            value.setFailed(ex);
            removeExpired(System.nanoTime());
            throw new CacheComputeException(ex.getMessage(), ex);
        }
        long nanoTime = System.nanoTime();
        value.setValue(rawValue, nanoTime+_ttl);
        removeExpired(nanoTime);
        return rawValue;
    }

    @Override
    public void put(K key, V rawValue) {
        long nanoTime = System.nanoTime();
        Value<V> value = new Value<>();
        value.setValue(rawValue, nanoTime+_ttl);
        Value<V> oldValue;
        synchronized ( this ) {
            oldValue = _map.put(key, value);
        }
        if ( null == oldValue ) {
            removeExpired(nanoTime);
            return;
        }
        // We might be in the middle of a compute that we want to
        // prempt:
        oldValue.setValue(rawValue, nanoTime+_ttl);
    }

    @Override
    public synchronized void remove(K key) {
        _map.remove(key);
    }

    @Override
    public synchronized int size() {
        return _map.size();
    }

    @Override
    public void resize(int max) {
        if ( max < 1 ) {
            synchronized ( this ) {
                _map.clear();
            }
            return;
        }

        while ( true ) {
            // Avoid holding the lock for more than MAX_REMOVED
            // iterations.
            synchronized ( this ) {
                Iterator<Value<V>> it = _map.values().iterator();
                int size = _map.size();
                for ( int i=0; i < MAX_REMOVED && size-- > max; i++ ) {
                    it.remove();
                }
            }
        }
    }
}
