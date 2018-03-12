package com.distelli.objectStore.impl;

import javax.inject.Provider;
import javax.inject.Named;
import javax.inject.Inject;
import com.distelli.cred.CredPair;
import com.distelli.cred.CredProvider;
import java.net.URI;
import java.util.Base64;
import java.io.File;
import com.distelli.objectStore.*;

public class ObjectStoreFactoryProvider implements Provider<ObjectStore.Factory> {
    @Inject @Named("BASE")
    private ObjectStore.Factory _baseObjectStoreFactory;

    private ObjectStoreType _osType;

    protected ObjectStoreFactoryProvider(ObjectStoreType osType) {
        _osType = osType;
    }

    @Override
    public ObjectStore.Factory get() {
        if ( ObjectStoreType.DISK == _osType ) return getDisk();

        CredPair credPair = getCredPair(_osType);
        URI endpoint = getEndpoint(_osType);
        CredProvider credProvider = () -> credPair;

        return new ObjectStore.Factory() {
            @Override
            public ObjectStore.Builder create() {
                return _baseObjectStoreFactory.create()
                    .withObjectStoreType(_osType)
                    .withEndpoint(endpoint)
                    .withCredProvider(credProvider);
            }
        };
    }

    private ObjectStore.Factory getDisk() {
        File dir =
            new File(System.getProperty("java.io.tmpdir"), "ObjectStore.Test");
        return new ObjectStore.Factory() {
            @Override
            public ObjectStore.Builder create() {
                return _baseObjectStoreFactory.create()
                    .withObjectStoreType(ObjectStoreType.DISK)
                    .withDiskStorageRoot(dir);
            }
        };
    }

    private URI getEndpoint(ObjectStoreType osType) {
        String envName = String.format("%s_ENDPOINT", osType);
        String endpoint = System.getenv(envName);
        if ( null == endpoint ) {
            throw new IllegalStateException("Please set the "+envName+" environment variable to the endpoint URI");
        }
        return URI.create(endpoint);
    }

    private CredPair getCredPair(ObjectStoreType osType) {
        String envName = String.format("%s_CREDS", osType);
        String credStr = System.getenv(envName);
        if ( null == credStr ) {
            throw new IllegalStateException("Please set the "+envName+" environment variable to the <keyId>=<secret> credential pair");
        }
        String[] credArr = credStr.split("=", 2);
        return new CredPair()
            .withKeyId(credArr[0])
            .withSecret(credArr.length > 1 ? credArr[1] : null);
    }
}
