package com.distelli.objectStore.impl;

import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.s3.S3ObjectStore;
import java.net.URI;
import com.distelli.cred.CredProvider;
import javax.inject.Inject;

public class ObjectStoreBuilder implements ObjectStore.Builder {
    private URI endpoint;
    private CredProvider credProvider;
    private URI proxy;
    private ObjectStoreType objectStoreProvider = ObjectStoreType.S3;
    private Boolean serverSideEncryption;
    private Boolean forceV4Signature;

    public interface Factory {
        public ObjectStoreBuilder create();
    }

    @Inject
    private S3ObjectStore.Factory _s3Factory;

    @Override
    public ObjectStore.Builder withEndpoint(URI endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @Override
    public ObjectStore.Builder withCredProvider(CredProvider credProvider) {
        this.credProvider = credProvider;
        return this;
    }

    @Override
    public ObjectStore.Builder withProxy(URI proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public ObjectStore.Builder withObjectStoreType(ObjectStoreType objectStoreProvider) {
        this.objectStoreProvider = objectStoreProvider;
        return this;
    }

    @Override
    public ObjectStore.Builder withForceV4Signature(Boolean forceV4Signature) {
        this.forceV4Signature = forceV4Signature;
        return this;
    }

    @Override
    public ObjectStore.Builder withServerSideEncryption(Boolean serverSideEncryption) {
        this.serverSideEncryption = serverSideEncryption;
        return this;
    }

    @Override
    public ObjectStore build() {
        switch ( objectStoreProvider ) {
        case S3:
        default:
            return _s3Factory.create(this);
        }
    }

    public URI getProxy() {
        return proxy;
    }

    public CredProvider getCredProvider() {
        return credProvider;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public Boolean getServerSideEncryption() {
        return serverSideEncryption;
    }

    public Boolean getForceV4Signature() {
        return forceV4Signature;
    }
}
