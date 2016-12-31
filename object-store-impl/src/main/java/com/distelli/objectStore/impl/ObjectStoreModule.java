package com.distelli.objectStore.impl;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.distelli.objectStore.*;
import com.distelli.objectStore.impl.s3.S3ObjectStore;
import com.distelli.objectStore.impl.disk.DiskObjectStore;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.Key;

public class ObjectStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.Builder.class, ObjectStoreBuilder.class)
                .build(Key.get(ObjectStore.Factory.class, Names.named("BASE"))));
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.class, S3ObjectStore.class)
                .build(S3ObjectStore.Factory.class));
        install(new FactoryModuleBuilder()
                .implement(ObjectStore.class, DiskObjectStore.class)
                .build(DiskObjectStore.Factory.class));
    }
}
