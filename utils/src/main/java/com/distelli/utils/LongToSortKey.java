package com.distelli.utils;

/**
 * B64 encode a long. Useful for sort keys that are sorced from a long.
 */
public class LongToSortKey {
    private static final char[] ALPHABET =
        "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        .toCharArray();

    public static String longToSortKey(long num) {
        // ceil(64bits / 6bits) => 11
        char[] result = new char[11];

        int pos = 0;
        for ( int shift=60; shift >= 0; shift -= 6 ) {
            result[pos++] = ALPHABET[(int)(num >>> shift) & 0x3F];
        }
        return new String(result);
    }
}
