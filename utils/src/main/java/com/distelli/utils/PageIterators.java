package com.distelli.utils;

import com.distelli.persistence.PageIterator;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.util.function.Predicate;

public class PageIterators {
    public static <T> List<T> filtered(
        Supplier<List<? extends T>> search,
        Predicate<? super T> filter,
        PageIterator it)
    {
        List<T> results = new ArrayList<>();
        int pageSize = it.getPageSize();
        while ( it.hasNext() && it.getPageSize() > 0 ) {
            results.addAll(
                search.get().stream()
                .filter(filter)
                .collect(Collectors.toList()));
            it.pageSize(pageSize - results.size());
        }
        it.pageSize(pageSize);
        return results;
    }
}
