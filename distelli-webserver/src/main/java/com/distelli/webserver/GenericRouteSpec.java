package com.distelli.webserver;

import java.util.function.UnaryOperator;

public class GenericRouteSpec<T>
{
    private String _path = null;
    private HTTPMethod _httpMethod = null;
    private T _value = null;

    public static <T> Builder<T, GenericRouteSpec<T>> builder() {
        return new Builder<T, GenericRouteSpec<T>>(GenericRouteSpec<T>::new);
    }

    public static class Builder<T, RS extends GenericRouteSpec<T>> {
        private RS _proto;
        private UnaryOperator<RS> _copy;

        public Builder(UnaryOperator<RS> copy) {
            _copy = copy;
            _proto = copy.apply(null);
        }
        private GenericRouteSpec<T> proto() {
            return _proto;
        }
        public Builder<T, RS> withPath(String path) {
            proto()._path = path;
            return this;
        }
        public Builder<T, RS> withHTTPMethod(HTTPMethod httpMethod) {
            proto()._httpMethod = httpMethod;
            return this;
        }
        public Builder<T, RS> withValue(T value) {
            proto()._value = value;
            return this;
        }
        public RS build() {
            return _copy.apply(_proto);
        }
    }

    protected GenericRouteSpec(GenericRouteSpec<T> copy) {
        if ( null == copy ) return;
        _path = copy._path;
        _httpMethod = copy._httpMethod;
        _value = copy._value;
    }

    public String getPath() {
        return this._path;
    }

    public HTTPMethod getHttpMethod() {
        return this._httpMethod;
    }

    public T getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        return String.format("RouteSpec[path=%s, httpMethod=%s, value=%s]",
                             _path,
                             _httpMethod,
                             _value);
    }
}
