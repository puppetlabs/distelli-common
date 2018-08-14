package com.distelli.objectStore;

import lombok.Data;
import lombok.Builder;
import com.distelli.cred.CredProvider;
import java.net.URI;
import java.io.File;

@Data @Builder
public class ObjectStoreConfig {
    public interface Factory {
        public ObjectStoreConfig create(File file);
    }
    private ObjectStoreType type;
    private File diskStorageRoot;
    private CredProvider credProvider;
    private URI endpoint;
    private String keyPrefix;
    private Boolean forceV4Signature;
    private Boolean serverSideEncryption;
    private URI proxy;
    private String bucket;
}
