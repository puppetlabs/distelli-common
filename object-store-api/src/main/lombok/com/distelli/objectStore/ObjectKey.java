package com.distelli.objectStore;

import lombok.Data;
import lombok.Builder;

@Data @Builder
public class ObjectKey {
    private String bucket;
    private String key;
}
