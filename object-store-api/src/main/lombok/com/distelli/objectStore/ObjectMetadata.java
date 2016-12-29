package com.distelli.objectStore;

import lombok.Data;
import lombok.Builder;

@Data @Builder
public class ObjectMetadata {
    private String bucket;
    private String key;
    private Long contentLength;
}
