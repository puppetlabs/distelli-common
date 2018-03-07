package com.distelli.webserver;

import java.util.function.UnaryOperator;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.Collections;

public class GenericFilterSpec<T> implements Comparable<GenericFilterSpec<T>>
{
    private String _name = null;
    private int _priority = Integer.MAX_VALUE;
    private Set<String> _after = Collections.emptySet();
    private T _value = null;

    public static <T> Builder<T, GenericFilterSpec<T>> builder() {
        return new Builder<T, GenericFilterSpec<T>>(GenericFilterSpec<T>::new);
    }

    public static class Builder<T, FS extends GenericFilterSpec<T>> {
        private FS _proto;
        private UnaryOperator<FS> _copy;

        public Builder(UnaryOperator<FS> copy) {
            _copy = copy;
            _proto = copy.apply(null);
        }
        private GenericFilterSpec<T> proto() {
            return _proto;
        }
        public Builder<T, FS> withName(String name) {
            proto()._name = name;
            return this;
        }
        public Builder<T, FS> withPriority(int priority) {
            proto()._priority = priority;
            return this;
        }
        public Builder<T, FS> withAfter(String... names) {
            return withAfter(Arrays.asList(names));
        }
        public Builder<T, FS> withAfter(Collection<String> names) {
            if ( null == names ) {
                proto()._after = Collections.emptySet();
            } else {
                proto()._after = Collections.unmodifiableSet(new LinkedHashSet<>(names));
            }
            return this;
        }
        public Builder<T, FS> withValue(T value) {
            proto()._value = value;
            return this;
        }
        public FS build() {
            return _copy.apply(_proto);
        }
    }

    protected GenericFilterSpec(GenericFilterSpec<T> copy) {
        if ( null == copy ) return;
        _name = copy._name;
        _priority = copy._priority;
        _after = copy._after;
        _value = copy._value;
    }

    public int getPriority() {
        return this._priority;
    }

    public String getName() {
        return this._name;
    }

    public Set<String> getAfter() {
        return this._after;
    }

    public T getValue() {
        return _value;
    }

    @Override
    public String toString() {
        return String.format("RouteSpec[name=%s, priority=%s, after=%s, value=%s]",
                             _name,
                             _priority,
                             _after,
                             _value);
    }

    @Override
    public int compareTo(GenericFilterSpec<T> other) {
        if ( this == other ) return 0;
        if ( null == other ) return -1;
        // Priority?
        if ( _priority != other._priority ) {
            if ( _priority < other._priority ) return -1;
            return 1;
        }
        // Name?
        if ( null == _name ) {
            if ( null != other._name ) return 1;
        } else if ( null == other._name ) {
            return -1; // null sorts last
        } else {
            int result = _name.compareTo(other._name);
            if ( 0 != result ) return result;
        }
        throw new IllegalStateException(
            "Two filter specs contain the same name and priority: "+
            this+" "+other);
    }
}
