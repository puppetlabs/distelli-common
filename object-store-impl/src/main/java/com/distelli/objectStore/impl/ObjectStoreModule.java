package com.distelli.objectStore.impl;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.s3.S3ObjectStore;
import com.distelli.objectStore.impl.disk.DiskObjectStore;
import com.distelli.objectStore.impl.artifactory.ArtifactoryObjectStore;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.Key;
import java.util.concurrent.ExecutorService;
import java.io.File;
import javax.inject.Provider;
import javax.inject.Inject;

public class ObjectStoreModule extends AbstractModule {
    private Provider<ObjectStoreConfig> _configProvider;

    public ObjectStoreModule() {}

    public ObjectStoreModule(String file) {
        this(null == file ? null : new File(file));
    }

    public ObjectStoreModule(File file) {
        this(null == file ? null : new Provider<ObjectStoreConfig>() {
                @Inject
                private ObjectStoreConfig.Factory _factory;
                @Override
                public ObjectStoreConfig get() {
                    return _factory.create(file);
                }
            });
    }

    public ObjectStoreModule(ObjectStoreConfig config) {
        this(() -> config);
    }

    public ObjectStoreModule(Provider<ObjectStoreConfig> configProvider) {
        _configProvider = configProvider;
    }

    @Override
    protected void configure() {
        bind(ObjectStore.Builder.class)
            .to(ObjectStoreBuilder.class);
        bind(Key.get(ObjectStore.Factory.class, Names.named("BASE")))
            .toInstance(
                new ObjectStore.Factory() {
                    @Inject
                    private Provider<ObjectStore.Builder> _builder;
                    @Override
                    public ObjectStore.Builder create() {
                        return _builder.get();
                    }
                });
        if ( null != _configProvider ) {
            bind(ObjectStoreConfig.class).toProvider(_configProvider);
            bind(ObjectStore.Factory.class).to(ObjectStoreFactoryImpl.class);
        }
        bind(ObjectStoreConfig.Factory.class).to(ObjectStoreConfigFactoryImpl.class);
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.class, S3ObjectStore.class)
                .build(S3ObjectStore.Factory.class));
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.class, DiskObjectStore.class)
                .build(DiskObjectStore.Factory.class));
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.class, ArtifactoryObjectStore.class)
                .build(ArtifactoryObjectStore.Factory.class));

        requireBinding(ExecutorService.class);
    }
}
