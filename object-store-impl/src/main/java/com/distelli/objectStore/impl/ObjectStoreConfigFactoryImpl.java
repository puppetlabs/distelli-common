package com.distelli.objectStore.impl;

import com.distelli.objectStore.*;
import java.io.File;
import javax.json.Json;
import javax.json.JsonObject;
import java.io.FileInputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import javax.inject.Singleton;
import java.net.URI;
import com.distelli.cred.CredProvider;
import com.distelli.cred.CredPair;
import com.distelli.aws.CredProviderAws;

@Singleton
public class ObjectStoreConfigFactoryImpl implements ObjectStoreConfig.Factory {
    protected ObjectStoreConfigFactoryImpl() {}

    @Override
    public ObjectStoreConfig create(File file) {
        if ( ! file.exists() ) {
            throw new IllegalArgumentException("Expected file '"+file+"' to exist");
        }
        JsonObject obj;
        try ( FileInputStream is = new FileInputStream(file) ) {
            obj = Json.createReader(is).readObject();
        } catch ( IOException ex ) {
            throw new UncheckedIOException(ex);
        }
        ObjectStoreType type = toObjectStoreType(getString(obj, "type"));
        return ObjectStoreConfig.builder()
            .type(type)
            .diskStorageRoot(toFile(getString(obj, "diskStorageRoot")))
            .credProvider(getCredProvider(obj, type, file))
            .endpoint(toURI(getString(obj, "endpoint")))
            .keyPrefix(getString(obj, "keyPrefix"))
            .forceV4Signature(getBoolean(obj, "forceV4Signature"))
            .serverSideEncryption(getBoolean(obj, "serverSideEncryption"))
            .proxy(toURI(getString(obj, "proxy")))
            .bucket(getString(obj, "bucket"))
            .build();
    }

    private static URI toURI(String str) {
        if ( null == str ) return null;
        return URI.create(str);
    }

    private static File toFile(String str) {
        if ( null == str ) return null;
        return new File(str);
    }

    private static Boolean getBoolean(JsonObject obj, String field) {
        if ( null == obj || ! obj.containsKey(field) ) return null;
        try {
            return obj.getBoolean(field);
        } catch ( ClassCastException ex ) {
            String str = getString(obj, field);
            if ( null == str ) return null;
            switch ( str.toLowerCase() ) {
            case "true":  return Boolean.TRUE;
            case "false": return Boolean.FALSE;
            }
            return null;
        }
    }

    private static ObjectStoreType toObjectStoreType(String type) {
        if ( null == type ) return null;
        try {
            return ObjectStoreType.valueOf(type);
        } catch ( IllegalArgumentException ex ) {
            return null;
        }
    }

    private static String getString(JsonObject obj, String field) {
        if ( null == obj || ! obj.containsKey(field) ) return null;
        try {
            return obj.getString(field);
        } catch ( ClassCastException ex ) {
            return null;
        }
    }

    private static CredProvider getCredProvider(JsonObject obj, ObjectStoreType type, File file) {
        if ( null == type ) return null;
        switch ( type ) {
        case S3:           return getS3CredProvider(obj, file);
        case DISK:         return null;
        case ARTIFACTORY:  return getArtifactoryCredProvider(obj, file);
        default:
            throw new UnsupportedOperationException(
                "ObjectStoreType="+type+" is not supported when parsing credProvider");
        }
    }

    private static CredProvider getS3CredProvider(JsonObject obj, File file) {
        String profileName = getString(obj, "profileName");
        String awsAccessKeyId = getString(obj, "awsAccessKeyId");
        String awsSecretAccessKey = getString(obj, "awsSecretAccessKey");
        if ( null != profileName ) {
            if ( null != awsAccessKeyId || null != awsSecretAccessKey ) {
                throw new IllegalArgumentException(
                    "Expected 'awsAccessKeyId' and 'awsSecretAccessKey' "+
                    "to not be defined since 'profileName' is set  in "+file);
            }
            return new CredProviderAws(profileName);
        }
        if ( null == awsAccessKeyId ^ null == awsSecretAccessKey ) {
            throw new IllegalArgumentException(
                "Expected 'awsAccessKeyId' AND 'awsSecretAccessKey'"+
                " to both be set (or both not set) in "+file);
        }
        if ( null != awsAccessKeyId && null != awsSecretAccessKey ) {
            CredPair credPair = new CredPair()
                .withKeyId(awsAccessKeyId)
                .withSecret(awsSecretAccessKey);
            return () -> credPair;
        }
        return new CredProviderAws();
    }

    private static CredProvider getArtifactoryCredProvider(JsonObject obj, File file) {
        String apiKey = getString(obj, "apiKey");
        if ( null != apiKey ) {
            CredPair credPair = new CredPair()
                .withSecret(apiKey);
            return () -> credPair;
        }
        throw new IllegalArgumentException(
            "Expected 'apiKey' to be defined in "+file+" to be set");
    }
}
