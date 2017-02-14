package com.distelli.utils;

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static com.distelli.utils.LongToSortKey.longToSortKey;

public class TestLongToSortKey
{
    @Test
    public void test() {
        final int range = 10;
        // Zero should always be sorted first:
        long num = 0;
        String lastSortKey = longToSortKey(num++);
        while ( num < range ) {
            String sortKey = longToSortKey(num++);
            assertThat(sortKey, greaterThan(lastSortKey));
            lastSortKey = sortKey;
        }
        // ...up to the max value:
        num = -range + Long.MAX_VALUE;
        boolean done = false;
        while ( num > 0 ) { // wait for overflow.
            String sortKey = longToSortKey(num++);
            assertThat(sortKey, greaterThan(lastSortKey));
            lastSortKey = sortKey;
        }
        // Negatives are next and should be in 2s complement form:
        num = Long.MIN_VALUE;
        while ( num <= Long.MIN_VALUE + range ) {
            String sortKey = longToSortKey(num++);
            assertThat(sortKey, greaterThan(lastSortKey));
            lastSortKey = sortKey;
        }
        // Finally, the negatives counting before 0:
        num = -range;
        while ( num < 0 ) {
            String sortKey = longToSortKey(num++);
            assertThat(sortKey, greaterThan(lastSortKey));
            lastSortKey = sortKey;
        }
    }
}
