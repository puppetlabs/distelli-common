package com.distelli.objectStore;

import lombok.Data;
import lombok.Builder;

@Data @Builder
public class ObjectPartKey {
    private String bucket;
    private String key;
    private String uploadId;
}
