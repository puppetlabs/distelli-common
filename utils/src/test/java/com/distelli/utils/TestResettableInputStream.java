package com.distelli.utils;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Arrays;
import java.io.ByteArrayInputStream;

public class TestResettableInputStream {
    @Test
    public void test() throws Exception {
        ByteArrayInputStream wrapped = new ByteArrayInputStream(
            "abc\ndef\n".getBytes(UTF_8));
        ResettableInputStream is = new ResettableInputStream(wrapped);

        // we shouldn't have read anything yet:
        assertThat(is.size(), equalTo(0L));

        // Read it all:
        byte[] buff = new byte[10];
        int size = is.read(buff, 0, buff.length);
        assertThat(size, equalTo(8));
        assertThat(Arrays.copyOf(buff, size), equalTo("abc\ndef\n".getBytes(UTF_8)));

        assertThat(is.read(buff, 0, buff.length), equalTo(-1));
        assertThat(is.available(), equalTo(0));

        // Reset:
        is.reset();
        assertThat(is.size(), equalTo(8L));
        assertThat(is.available(), equalTo(8));

        // Read it again:
        buff = new byte[10];
        size = is.read(buff, 0, buff.length);
        assertThat(size, equalTo(8));
        assertThat(Arrays.copyOf(buff, size), equalTo("abc\ndef\n".getBytes(UTF_8)));

        assertThat(is.read(buff, 0, buff.length), equalTo(-1));
    }

    @Test
    public void testBoundaryRead() throws Exception {
        ByteArrayInputStream wrapped = new ByteArrayInputStream(
            "abc\ndef\n".getBytes(UTF_8));
        ResettableInputStream is = new ResettableInputStream(wrapped);

        byte[] buff = new byte[3];
        int size = is.read(buff, 0, buff.length);
        assertThat(size, equalTo(3));
        assertThat(buff, equalTo("abc".getBytes(UTF_8)));

        is.mark(Integer.MAX_VALUE);

        size = is.read(buff, 0, buff.length);
        assertThat(size, equalTo(3));
        assertThat(buff, equalTo("\nde".getBytes(UTF_8)));

        assertThat(is.skip(1), equalTo(1L));

        is.reset();

        buff = new byte[10];
        size = is.read(buff, 0, buff.length);
        assertThat(size, equalTo(5));
        assertThat(Arrays.copyOf(buff, size), equalTo("\ndef\n".getBytes(UTF_8)));

        // END OF FILE:
        assertThat(is.read(buff, 0, buff.length), equalTo(-1));
        assertThat(is.skip(1), equalTo(0L));

        is.reset();
        assertThat(is.available(), equalTo(5));
    }
}
