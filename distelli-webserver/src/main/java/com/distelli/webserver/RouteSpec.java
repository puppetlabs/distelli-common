package com.distelli.webserver;

import javax.servlet.http.HttpServlet;

public class RouteSpec extends GenericRouteSpec<Class<? extends RequestHandler>>
{
    public static class Builder
    {
        private GenericRouteSpec.Builder<Class<? extends RequestHandler>, RouteSpec> _builder =
            new GenericRouteSpec.Builder<Class<? extends RequestHandler>, RouteSpec>(RouteSpec::new);

        public Builder withPath(String path) {
            _builder.withPath(path);
            return this;
        }
        public Builder withHTTPMethod(HTTPMethod httpMethod) {
            _builder.withHTTPMethod(httpMethod);
            return this;
        }
        public Builder withRequestHandler(Class<? extends RequestHandler> requestHandler) {
            _builder.withValue(requestHandler);
            return this;
        }
        public RouteSpec build() {
            return _builder.build();
        }
    }

    public RouteSpec(GenericRouteSpec<Class<? extends RequestHandler>> copy) {
        super(copy);
    }

    public RouteSpec(RouteSpec copy) {
        super(copy);
    }

    public Class<? extends RequestHandler> getRequestHandler()
    {
        return getValue();
    }

    @Override
    public String toString()
    {
        return String.format("RouteSpec[path=%s, httpMethod=%s, requestHandler=%s",
                             getPath(),
                             getHttpMethod(),
                             getValue());
    }
}
