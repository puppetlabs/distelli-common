/*
  $Id: $
  @file TestCompositKey.java
  @brief Contains the TestCompositKey.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.utils;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class TestCompositeKey
{
    @Test
    public void testBuildStrings()
    {
        String[] parts = { "a", "b", "c"};
        String key = CompositeKey.build(parts);
        assertThat(key, equalTo("a\u001eb\u001ec"));
    }

    @Test
    public void testBuildLongs()
    {
        long[] parts = { 1l, 2l, 3l};
        String key = CompositeKey.build(parts);
        assertThat(key, equalTo("........../\u001e..........0\u001e..........1"));
    }

    @Test
    public void testBuildMixed()
    {
        String partA = "a";
        long part2 = 2l;
        String partB = "b";
        String key = new CompositeKey.Builder()
        .withString(partA)
        .withLong(part2)
        .withString(partB)
        .build();

        assertThat(key, equalTo("a\u001e..........0\u001eb"));
    }

    @Test
    public void testSplitStrings()
    {
        String key = "a\u001eb\u001ec";
        String[] parts = CompositeKey.split(key);
        assertThat(parts.length, equalTo(3));
        assertThat(parts[0], equalTo("a"));
        assertThat(parts[1], equalTo("b"));
        assertThat(parts[2], equalTo("c"));
    }

    @Test
    public void testSplitLongs()
    {
        String key = "........../\u001e..........0\u001e..........1";
        long[] parts = CompositeKey.splitLongs(key);
        assertThat(parts.length, equalTo(3));
        assertThat(parts[0], equalTo(1l));
        assertThat(parts[1], equalTo(2l));
        assertThat(parts[2], equalTo(3l));
    }

    @Test
    public void testSplitStringsLimit()
    {
        String key = "a\u001eb\u001eHello\u001eWorld";
        String[] parts = CompositeKey.split(key, 3);
        assertThat(parts.length, equalTo(3));
        assertThat(parts[0], equalTo("a"));
        assertThat(parts[1], equalTo("b"));
        assertThat(parts[2], equalTo("Hello\u001eWorld"));
    }

    @Test
    public void buildPrefix()
    {
        String[] parts = { "a", "b", "c"};
        String key = CompositeKey.buildPrefix(parts);
        assertThat(key, equalTo("a\u001eb\u001ec\u001e"));
    }

    @Test
    public void testBuildPrefixLongs()
    {
        long[] parts = { 1l, 2l, 3l};
        String key = CompositeKey.buildPrefix(parts);
        assertThat(key, equalTo("........../\u001e..........0\u001e..........1\u001e"));
    }

    @Test
    public void testBuildStringLong()
    {
        String key = CompositeKey
        .builder()
        .withString(1L)
        .withString("a")
        .withString(2L)
        .build();
        assertThat(key, equalTo("1\u001ea\u001e2"));
    }

    @Test
    public void testBuildPrefixStringLong()
    {
        String key = CompositeKey
        .builder()
        .withString(1L)
        .withString("a")
        .withString(2L)
        .buildPrefix();
        assertThat(key, equalTo("1\u001ea\u001e2\u001e"));
    }
}
