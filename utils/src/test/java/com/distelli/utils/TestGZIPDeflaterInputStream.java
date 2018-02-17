package com.distelli.utils;

import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TestGZIPDeflaterInputStream {
    @Test
    public void test() throws IOException {
        for ( int i=0; i < 200; i++ ) {
            String testInput = String.join("", Collections.nCopies(i, "x"));

            // Read single chars:
            InputStream in = new GZIPDeflaterInputStream(
                new ByteArrayInputStream(
                    testInput.getBytes(UTF_8)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            readSingleChars(in, out);

            assertEquals("i="+i, testInput, decompress(out.toByteArray()));
            // Read multiple chars:
            for ( int size=1; size < 7; size++ ) {
                for ( int offset=0; offset < 2; offset++ ) {
                    in = new GZIPDeflaterInputStream(
                        new ByteArrayInputStream(
                            testInput.getBytes(UTF_8)));
                    out = new ByteArrayOutputStream();
                    readMultipleChars(in, out, offset, size);
                    assertEquals(
                        "i="+i+" size="+size+" offset="+offset,
                        testInput,
                        decompress(out.toByteArray()));
                }
            }
        }
    }

    private String decompress(byte[] buff) throws IOException {
        InputStream in = new GZIPInputStream(new ByteArrayInputStream(buff));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        readMultipleChars(in, out, 0, 100);
        return new String(out.toByteArray(), UTF_8);
    }

    private void readSingleChars(InputStream in, OutputStream out) throws IOException {
        while ( true ) {
            int ch = in.read();
            if ( ch < 0 ) break;
            out.write(ch);
        }
    }

    private void readMultipleChars(InputStream in, OutputStream out, int offset, int size)
        throws IOException
    {
        byte[] buff = new byte[size+offset];
        while ( true ) {
            int len = in.read(buff, offset, size);
            if ( len < 0 ) break;
            out.write(buff, offset, len);
        }
    }

    public static void prettyOut(byte[] msg) {
        System.out.println("msg size="+msg.length);
        for (int j = 1; j < msg.length+1; j++) {
            if (j % 8 == 1 || j == 0) {
                if( j != 0){
                    System.out.println();
                }
                System.out.format("0%d\t|\t", j / 8);
            }
            System.out.format(" %02X", msg[j-1]);
            if (j % 4 == 0) {
                System.out.print(" ");
            }
        }
        System.out.println();
    }
}
