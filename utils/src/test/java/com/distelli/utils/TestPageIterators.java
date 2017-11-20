package com.distelli.utils;

import static com.distelli.utils.PageIterators.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.distelli.persistence.PageIterator;
import java.util.List;
import java.util.ArrayList;

public class TestPageIterators {
    public class Base {
        public int id;
        @Override
        public String toString() {
            return ""+id;
        }
    }
    public class Extends extends Base {
        public String name;
    }

    public List<Extends> search(PageIterator it) {
        List<Extends> result = new ArrayList<>();
        String marker = it.getMarker();
        int begin = 0;
        if ( null != marker ) {
            begin = Integer.parseInt(marker);
        }
        for ( begin++ ; begin < 50 && result.size() < it.getPageSize(); begin++ ) {
            String thisName = ""+begin;
            int thisId = begin;
            result.add(new Extends() {{
                name = thisName;
                id = thisId;
            }});
        }
        System.err.println("search marker="+it.getMarker()+" results="+result);
        if ( result.size() > 0 ) {
            it.setMarker(result.get(result.size()-1).name);
        } else {
            it.setMarker(null);
        }
        return result;
    }

    @Test
    public void test() {
        PageIterator it = new PageIterator()
            .marker("3")
            .pageSize(10);
        
        List<Base> result = filtered(
            () -> search(it),
            (elm) -> elm.id % 3 == 0,
            it); // 4,5 [6] ... [33]
        System.err.println("Final result="+result);
        assertEquals(result.size(), 10);
        for ( int num=6, idx=0; idx < result.size(); num+=3, idx++ ) { 
            assertEquals(result.get(idx).id, num);
        }
        assertEquals(it.getPageSize(), 10);
        assertEquals(it.getMarker(), "33");
    }
}
