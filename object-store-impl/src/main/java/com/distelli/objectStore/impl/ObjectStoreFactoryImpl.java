package com.distelli.objectStore.impl;

import com.distelli.objectStore.*;
import javax.inject.Singleton;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Named;
import javax.annotation.Nullable;

@Singleton
public class ObjectStoreFactoryImpl implements ObjectStore.Factory {
    @Inject @Nullable
    private Provider<ObjectStoreConfig> _configProvider;
    @Inject @Named("BASE")
    private ObjectStore.Factory _factory;

    @Override
    public ObjectStore.Builder create() {
        ObjectStoreConfig config = null == _configProvider ? null : _configProvider.get();
        if ( null == config ) {
            return _factory.create();
        }
        return _factory.create()
            .withDiskStorageRoot(config.getDiskStorageRoot())
            .withEndpoint(config.getEndpoint())
            .withCredProvider(config.getCredProvider())
            .withProxy(config.getProxy())
            .withObjectStoreType(config.getType())
            .withForceV4Signature(config.getForceV4Signature())
            .withServerSideEncryption(config.getServerSideEncryption());
    }

    @Override
    public ObjectKey createKey(String key) {
        ObjectStoreConfig config = null == _configProvider ? null : _configProvider.get();
        if ( null == config || null == config.getBucket() ) {
            throw new MissingObjectStoreConfigException(
                "No ObjectStore configuration is defined (must specify bucket)");
        }
        return ObjectKey.builder()
            .bucket(config.getBucket())
            .key(addPrefix(config.getKeyPrefix(), key))
            .build();
    }

    private static String addPrefix(String prefix, String key) {
        if ( null == prefix ) return key;
        // Strip "/" from beginning and end of prefix:
        int start = 0;
        for (; prefix.length() > start && '/' == prefix.charAt(start); start++ );
        int end = prefix.length();
        for (; end > start && '/' == prefix.charAt(end-1); end-- );
        // Empty string, just return key:
        if ( end <= start ) return key;
        return prefix.substring(start, end) + "/" + key;
    }
}
