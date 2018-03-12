package com.distelli.objectStore.impl;

import java.io.File;
import java.net.URI;
import javax.inject.Inject;

import com.distelli.cred.CredProvider;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.s3.S3ObjectStore;
import com.distelli.objectStore.impl.artifactory.ArtifactoryObjectStore;
import com.distelli.objectStore.impl.disk.DiskObjectStore;

public class ObjectStoreBuilder implements ObjectStore.Builder {
    private URI endpoint;
    private CredProvider credProvider;
    private URI proxy;
    private ObjectStoreType objectStoreProvider = ObjectStoreType.S3;
    private Boolean serverSideEncryption;
    private Boolean forceV4Signature;
    private File diskStorageRoot;

    public interface Factory {
        public ObjectStoreBuilder create();
    }

    @Inject
    private S3ObjectStore.Factory _s3Factory;
    @Inject
    private DiskObjectStore.Factory _diskFactory;
    @Inject
    private ArtifactoryObjectStore.Factory _artifactoryFactory;

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
    public ObjectStore.Builder withDiskStorageRoot(File diskStorageRoot) {
        this.diskStorageRoot = diskStorageRoot;
        return this;
    }

    @Override
    public ObjectStore build() {
        switch ( objectStoreProvider ) {
        case S3:
            return _s3Factory.create(this);
        case DISK:
            return _diskFactory.create(this);
        case ARTIFACTORY:
            return _artifactoryFactory.create(this);
        default:
            throw(new RuntimeException("Unsupported ObjectStore Provider: "+objectStoreProvider));
        }
    }

    public ObjectStoreType getObjectStoreProvider() {
        return this.objectStoreProvider;
    }

    public File getDiskStorageRoot() {
        return this.diskStorageRoot;
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
