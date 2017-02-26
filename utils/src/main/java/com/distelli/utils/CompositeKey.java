/*
  $Id: $
  @file CompositeKey.java
  @brief Contains the CompositeKey.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CompositeKey
{
    public static final String DELIM = "\u001e";

    public static class Builder
    {
        private List<String> parts = new ArrayList<String>();
        public Builder withStrings(String... parts)
        {
            for(String part : parts)
                withString(part);
            return this;
        }

        public Builder withLongs(long... parts)
        {
            for(long part : parts)
                withLong(part);
            return this;
        }

        public Builder withString(String part)
        {
            this.parts.add(part);
            return this;
        }

        public Builder withString(long part)
        {
            this.parts.add(String.format("%d", part));
            return this;
        }

        public Builder withLong(long part)
        {
            this.parts.add(LongSortKey.longToSortKey(part));
            return this;
        }

        public String build()
        {
            StringJoiner joiner = new StringJoiner(DELIM);
            for(String part : this.parts)
            {
                if(part == null)
                    part = "";
                joiner.add(part);
            }
            return joiner.toString();
        }

        public String buildPrefix()
        {
            return String.format("%s\u001e", build());
        }
    }

    public CompositeKey()
    {

    }

    public static CompositeKey.Builder builder()
    {
        return new CompositeKey.Builder();
    }

    public static String build(String... parts)
    {
        return builder().withStrings(parts).build();
    }
    public static String build(long... parts)
    {
        return builder().withLongs(parts).build();
    }

    public static long[] splitLongs(String key)
    {
        String[] strParts = key.split(DELIM);
        int index = 0;
        long[] longParts = new long[strParts.length];
        for(String part : strParts)
        {
            longParts[index] = LongSortKey.sortKeyToLong(part);
            index++;
        }
        return longParts;
    }

    public static String buildPrefix(String... parts)
    {
        return builder().withStrings(parts).buildPrefix();
    }

    public static String buildPrefix(long... parts)
    {
        return builder().withLongs(parts).buildPrefix();
    }

    public static String[] split(String key)
    {
        return key.split(DELIM);
    }

    public static String[] split(String key, int limit)
    {
        return key.split(DELIM, limit);
    }
}
