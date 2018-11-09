package com.distelli.webserver.proxy;

import com.distelli.webserver.GenericFilter;
import com.distelli.webserver.GenericFilterSpec;
import com.distelli.webserver.GenericRequestHandler;
import com.distelli.webserver.GenericRouteSpec;
import com.distelli.webserver.HTTPMethod;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import java.net.URI;
import javax.inject.Inject;
import javax.inject.Provider;

public class ProxyAllModule extends ProxyModule {
    private URI _proxyTo;
    public ProxyAllModule(String proxyTo) {
        _proxyTo = URI.create(proxyTo);
    }

    @Override
    protected void configure() {
        super.configure();

        // Handle /404 routes:
        Multibinder<GenericRouteSpec<GenericRequestHandler>> routeBinder = Multibinder.newSetBinder(
            binder(),
            new TypeLiteral<GenericRouteSpec<GenericRequestHandler>>(){});
        routeBinder.addBinding()
            .toProvider(new ProxyRouteSpecProvider(HTTPMethod.GET, "/404"));
        routeBinder.addBinding()
            .toProvider(new ProxyRouteSpecProvider(HTTPMethod.WEBSOCKET, "/404"));

        // Configuration:
        bind(ProxyConfig.class)
            .toInstance(
                new ProxyConfig() {
                    public URI getProxyTo() {
                        return _proxyTo;
                    }
                });

        // Avoid silly error about this map binder not existing...
        MapBinder<String, GenericFilterSpec<GenericFilter>> filterBinder = MapBinder.newMapBinder(
            binder(),
            new TypeLiteral<String>(){},
            new TypeLiteral<GenericFilterSpec<GenericFilter>>(){});
    }

    protected static class ProxyRouteSpecProvider implements Provider<GenericRouteSpec<GenericRequestHandler>> {
        @Inject
        private ProxyRequestHandler _handler;
        private HTTPMethod _method;
        private String _path;

        private ProxyRouteSpecProvider(HTTPMethod method, String path) {
            _method = method;
            _path = path;
        }

        @Override
        public GenericRouteSpec<GenericRequestHandler> get() {
            return GenericRouteSpec.<GenericRequestHandler>builder()
                .withPath(_path)
                .withHTTPMethod(_method)
                .withValue(_handler)
                .build();
        }
    }
}
