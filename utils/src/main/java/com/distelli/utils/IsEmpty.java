package com.distelli.utils;

import java.util.Collection;
import java.util.Map;

public class IsEmpty {
    public static boolean isEmpty(String str) {
        return null == str || str.isEmpty();
    }
    public static boolean isEmpty(Collection<?> elms) {
        return null == elms || elms.isEmpty();
    }
    public static boolean isEmpty(Map<?,?> elms) {
        return null == elms || elms.isEmpty();
    }
}
