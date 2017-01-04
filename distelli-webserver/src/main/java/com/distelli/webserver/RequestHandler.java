package com.distelli.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;

public abstract class RequestHandler<R extends RequestContext>
{
    public RequestHandler()
    {

    }

    public abstract WebResponse handleRequest(R requestContext);

    public WebResponse redirect(String location)
    {
        return WebResponse.redirect(location);
    }

    public WebResponse redirect(WebResponse response, String location)
    {
        return WebResponse.redirect(response, location);
    }

    public WebResponse ok(String content)
    {
        return WebResponse.ok(content);
    }

    public WebResponse ok(WebResponse response)
    {
        return WebResponse.ok(response);
    }

    public WebResponse badRequest(String content)
    {
        return WebResponse.badRequest(content);
    }

    public WebResponse badRequest(WebResponse response)
    {
        return WebResponse.badRequest(response);
    }

    public WebResponse toJson(String key, String value)
    {
        return WebResponse.toJson(key, value);
    }

    public WebResponse notFound(String content)
    {
        return WebResponse.notFound(content);
    }

    public WebResponse ok(ResponseWriter responseWriter)
    {
        return WebResponse.ok(responseWriter);
    }

    public WebResponse toJson(ResponseWriter responseWriter)
    {
        return WebResponse.toJson(responseWriter);
    }

    public WebResponse toJson(final Object obj, int httpResponseCode)
    {
        return WebResponse.toJson(obj, httpResponseCode);
    }

    public WebResponse jsonError(JsonError jsonError)
    {
        return WebResponse.jsonError(jsonError);
    }

    public WebResponse toJson(final Object obj)
    {
        return WebResponse.toJson(obj, 200);
    }
}
