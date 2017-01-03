package com.distelli.objectStore;

import lombok.Data;
import lombok.Builder;

@Data @Builder
public class ObjectPartId {
    private Integer partNum; // passed into multipartPut().
    private String partId; // Returned by multipartPut()
}
