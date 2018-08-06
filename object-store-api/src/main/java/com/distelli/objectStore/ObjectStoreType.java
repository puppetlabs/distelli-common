package com.distelli.objectStore;

public enum ObjectStoreType
{
    S3,
    DISK,
    ARTIFACTORY;

    private static final ObjectStoreType[] values = values();

    public static ObjectStoreType valueOf(int ordinal) {
        if ( ordinal < 0 || ordinal >= values.length ) return null;
        return values[ordinal];
    }

}
