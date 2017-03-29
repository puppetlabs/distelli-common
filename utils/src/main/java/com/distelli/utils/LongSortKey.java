package com.distelli.utils;

/**
 * B64 encode a long. Useful for sort keys that are sorced from a long.
 */
public class LongSortKey {
    // ceil(64bits / 6bits) => 11
    public static int LONG_SORT_KEY_LENGTH = 11;
    private static final char[] ALPHABET =
        "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        .toCharArray();

    public static String longToSortKey(long num) {
        char[] result = new char[LONG_SORT_KEY_LENGTH];

        int pos = 0;
        for ( int shift=60; shift >= 0; shift -= 6 ) {
            result[pos++] = ALPHABET[(int)(num >>> shift) & 0x3F];
        }
        return new String(result);
    }

    public static Long sortKeyToLong(String sortKey) {
        if ( null == sortKey ) return null;
        if ( sortKey.length() != LONG_SORT_KEY_LENGTH ) {
            throw new IllegalArgumentException(
                "Invalid longSortKey, must be string of length "+
                LONG_SORT_KEY_LENGTH+", got length="+sortKey.length());
        }
        char[] chrs = sortKey.toCharArray();
        long result = 0;
        int shift = 60;
        for ( int i=0; i < chrs.length; i++, shift -= 6 ) {
            result |= ord(chrs[i]) << shift;
        }
        return result;
    }

    private static long ord(char ch) {
        if ( ch >= '.' ) {
            if ( ch <= '9' ) {
                return ch - '.';
            } else if ( ch <= 'Z' ) {
                if ( ch >= 'A' ) {
                    return 12 + (ch - 'A');
                }
            } else if ( ch <= 'z' ) {
                if ( ch >= 'a' ) {
                    return 12 + 26 + (ch - 'a');
                }
            }
        }
        throw new IllegalArgumentException(
            "Invalid longSortKey, contained char='"+ch+"'");
    }
}
