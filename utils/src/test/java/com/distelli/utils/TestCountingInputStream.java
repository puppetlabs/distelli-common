package com.distelli.utils;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.ByteArrayInputStream;

public class TestCountingInputStream {
    @Test
    public void test() throws Exception {
        CountingInputStream is = new CountingInputStream(new ByteArrayInputStream("abc".getBytes()));
        while ( is.read() >= 0 );
        assertEquals(is.getCount(), 3);
        is.reset();
        assertEquals(is.getCount(), 0);
        while ( is.read() >= 0 );
        assertEquals(is.getCount(), 3);

        is.reset();
        byte[] buff = new byte[100];
        assertEquals(is.read(buff), 3);
        assertEquals(is.read(buff), -1);
        assertEquals(is.getCount(), 3);
        is.reset();
    }
}
