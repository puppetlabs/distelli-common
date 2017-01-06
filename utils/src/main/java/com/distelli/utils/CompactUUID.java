/*
  $Id: $
  @file CompactUUID.java
  @brief Contains the CompactUUID.java class

  All Rights Reserved.

  @author Rahul Singh [rsingh]
*/
package com.distelli.utils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;

public class CompactUUID
{
    private String _uuid = null;

    private static final char[] alphabet = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'};

    private CompactUUID(UUID uuid)
    {
        _uuid = toBase36(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    private CompactUUID()
    {
        this(UUID.randomUUID());
    }

    public static CompactUUID fromUUID(UUID uuid)
    {
        return new CompactUUID(uuid);
    }

    public static CompactUUID randomUUID()
    {
        return new CompactUUID();
    }

    @Override
    public String toString()
    {
        return _uuid;
    }

    public boolean equals(Object o)
    {
        if(o == null)
            return false;

        if(!(o instanceof CompactUUID))
            return false;

        CompactUUID other = (CompactUUID)o;
        return _uuid.equals(other._uuid);
    }

    @Override
    public int hashCode()
    {
        return _uuid.hashCode();
    }

    protected static String toBase36(long value, boolean padLeadingZeros)
    {
        if(value < 0)
            value += Long.MAX_VALUE;

        char[] buf = {'0','0','0','0','0','0','0','0','0','0','0','0','0'};
        if(value == 0)
        {
            if(!padLeadingZeros)
                return "0";
            return new String(buf);
        }

        int radix = 36;

        int charPos = 12;

        value = -value;
        while(value <= -radix)
        {
            buf[charPos--] = alphabet[(int)(-(value % radix))];
            value = value / radix;
        }

        buf[charPos] = alphabet[(int)(-value)];

        if(padLeadingZeros)
            return new String(buf);
        return new String(buf, charPos, (13 - charPos));
    }

    protected static String toBase36(long msb, long lsb)
    {
        return toBase36(msb, true)+toBase36(lsb, true);
    }
}
