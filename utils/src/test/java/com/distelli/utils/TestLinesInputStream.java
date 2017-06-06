package com.distelli.utils;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Arrays;

public class TestLinesInputStream {
    @Test
    public void testFullRead() {
        LinesInputStream lines = new LinesInputStream();
        lines.add("abc\n".getBytes(UTF_8));
        lines.add("def\n".getBytes(UTF_8));

        assertThat(lines.available(), equalTo(8));

        byte[] buff = new byte[10];
        int size = lines.read(buff, 0, buff.length);
        assertThat(size, equalTo(8));
        assertThat(Arrays.copyOf(buff, size), equalTo("abc\ndef\n".getBytes(UTF_8)));

        assertThat(lines.read(buff, 0, buff.length), equalTo(-1));
    }

    @Test
    public void testBoundaryRead() {
        LinesInputStream lines = new LinesInputStream();
        lines.add("abc\n".getBytes(UTF_8));
        lines.add("def\n".getBytes(UTF_8));
        byte[] buff = new byte[3];

        assertThat(lines.available(), equalTo(8));

        int size = lines.read(buff, 0, buff.length);
        assertThat(size, equalTo(3));
        assertThat(buff, equalTo("abc".getBytes(UTF_8)));
        assertThat(lines.available(), equalTo(5));

        lines.mark(Integer.MAX_VALUE);

        size = lines.read(buff, 0, buff.length);
        assertThat(size, equalTo(3));
        assertThat(buff, equalTo("\nde".getBytes(UTF_8)));
        assertThat(lines.available(), equalTo(2));

        assertThat(lines.skip(1), equalTo(1L));

        size = lines.read(buff, 0, buff.length);
        assertThat(size, equalTo(1));
        assertThat(Arrays.copyOf(buff, size), equalTo("\n".getBytes(UTF_8)));

        // END OF FILE:
        assertThat(lines.read(buff, 0, buff.length), equalTo(-1));
        assertThat(lines.skip(1), equalTo(0L));

        lines.reset();

        assertThat(lines.read(), equalTo((int)'\n'));

        buff = new byte[10];
        size = lines.read(buff, 0, buff.length);
        assertThat(size, equalTo(4));
        assertThat(Arrays.copyOf(buff, size), equalTo("def\n".getBytes(UTF_8)));

        assertThat(lines.read(buff, 0, buff.length), equalTo(-1));
    }
}
