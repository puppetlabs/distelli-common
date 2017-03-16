package com.distelli.utils;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Array;
import java.util.Collection;

public class TestTopoSort {
    @Test
    public void test() {
        // Make sure order is preserved:
        TopoSort<String> sortStrs = new TopoSort<String>()
            .add("ACEa")
            .add("ACEb")
            .add("ACEc")
            .add("ACEd")
            .add("ACEe")
            .add("ACEf")
            .add("ACEg")
            .add("ACEh")
            .add("ACEi");
        List<String> sorted = new ArrayList<>();
        sortStrs.reverseSort(sorted::add);
        assertArrayEquals(new String[] {"ACEa", "ACEb", "ACEc", "ACEd", "ACEe", "ACEf", "ACEg", "ACEh", "ACEi"},
                          toArray(sorted, String.class));
        TopoSort<Integer> sort = new TopoSort<Integer>()
            .add(5, 11)
            .add(7, 11)
            .add(7, 8)
            .add(3, 8)
            .add(3, 10)
            .add(11, 2)
            .add(11, 9)
            .add(11, 10)
            .add(8, 9)
            .add(2)
            .add(9)
            .add(10);
        List<Integer> result = new ArrayList<>();
        sort.reverseSort(result::add);
        assertArrayEquals(result.toArray(new Integer[result.size()]),
                          new Integer[] {2, 9, 8, 10, 3, 11, 5, 7});

        sort.remove(8); // 7 should now depend on 9:
        assertArrayEquals(sort(toArray(sort.getDependencies(7), Integer.class)),
                          new Integer[] {9, 11});

        sort = new TopoSort<Integer>()
            .add(1, 2)
            .add(2, 3)
            .add(3, 4)
            .add(4, 1);

        List<Integer> cycle = null;
        try {
            sort.reverseSort(result::add);
        } catch ( TopoSort.CycleDetectedException ex ) {
            cycle = (List<Integer>)ex.getCycle();
        }
        assertArrayEquals(cycle.toArray(new Integer[cycle.size()]),
                          new Integer[] {2, 3, 4, 1});

        sort = new TopoSort<Integer>()
            .add(1, 2)
            .add(2, 3)
            .add(3, 4)
            .add(4, 3);

        cycle = null;
        try {
            sort.reverseSort(result::add);
        } catch ( TopoSort.CycleDetectedException ex ) {
            cycle = (List<Integer>)ex.getCycle();
        }
        assertArrayEquals(cycle.toArray(new Integer[cycle.size()]),
                          new Integer[] {4, 3});
    }

    @Test
    public void testRemove() {
        TopoSort<String> sort = new TopoSort<String>()
            .add("A-SimpleBashApp-TEST", "BitbucketManager")
            .add("NodeExpress")
            .add("nodejs-docker", "NodeExpress")
            .add("Red", "Salsa")
            .add("Red", "FishStix")
            .add("Salsa", "SimpleBashAppHG")
            .add("SimpleBashAppHG", "Test")
            .add("Test", "nodejs-docker");
        List<String> sorted = new ArrayList<>();
        sort.reverseSort(sorted::add);
        System.err.println(sorted);
        sort.remove("BitbucketManager");
        sort.remove("A-SimpleBashApp-TEST");
        sort.remove("FishStix");
        sort.remove("NodeExpress");
        sort.remove("nodejs-docker");
        sort.remove("Test");
        sort.remove("SimpleBashAppHG");
        // Previously we saw NodeExpress in here
        assertArrayEquals(toArray(sort.getDependencies("Salsa"), String.class),
                          new String[0]);
    }

    private <T> T[] sort(T[] array) {
        Arrays.sort(array);
        return array;
    }

    private <T> T[] toArray(Collection<T> collection, Class<T> type) {
        return collection.toArray((T[])Array.newInstance(type, collection.size()));
    }
}
