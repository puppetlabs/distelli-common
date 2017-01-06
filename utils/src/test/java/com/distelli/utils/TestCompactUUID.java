/*
  $Id: $
  @file TestCompactUUID.java
  @brief Contains the TestCompactUUID.java class

  All Rights Reserved.

  @author Rahul Singh [rsingh]
*/
package com.distelli.utils;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class TestCompactUUID
{
    @Test
    public void testUUID()
    {
        long start = System.nanoTime();
        for(int i=0;i<100000;i++)
            CompactUUID.randomUUID().toString();

        long end = System.nanoTime();
        long time = (end - start)/1000000;
        System.out.println("CID: "+time+" ms");

        start = System.nanoTime();
        for(int i=0;i<100000;i++)
            UUID.randomUUID().toString();
        end = System.nanoTime();

        time = (end - start)/1000000;
        System.out.println("UUID: "+time+" ms");
    }

    @Test
    public void testAccuracy()
    {
        UUID uuid = UUID.randomUUID();
        String cuid = CompactUUID.fromUUID(uuid).toString();
        String fastUUID = CompactUUID.toBase36(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());

        assertThat(cuid, equalTo(fastUUID));
    }
}
