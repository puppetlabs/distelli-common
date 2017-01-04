package com.distelli.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.servlet.http.Cookie;

public class WebResponse
{
    private int _httpStatusCode = 200;
    private String _contentType = "text/html";
    private byte[] _responseContent;
    private Map<String, String> _responseHeaders = new TreeMap<String, String>();
    private ResponseWriter _responseWriter = null;
    private List<Cookie> _cookies = new ArrayList<Cookie>();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        // Support deserializing interfaces:
        OBJECT_MAPPER.registerModule(new MrBeanModule());
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.getJsonFactory().setCharacterEscapes(new HTMLCharacterEscapes());
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public WebResponse() {}

    public WebResponse(int httpStatusCode)
    {
        _httpStatusCode = httpStatusCode;
    }

    public WebResponse(String responseContent)
    {
        _responseContent = responseContent.getBytes();
    }

    public WebResponse(int httpStatusCode, String responseContent)
    {
        _responseContent = responseContent.getBytes();
        _httpStatusCode = httpStatusCode;
    }

    public int getHttpStatusCode() {
        return this._httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode) {
        this._httpStatusCode = httpStatusCode;
    }

    public String getContentType() {
        return this._contentType;
    }

    public void setContentType(String contentType) {
        this._contentType = contentType;
    }

    public byte[] getResponseContent() {
        return this._responseContent;
    }

    public void setResponseContent(byte[] responseContent) {
        this._responseContent = responseContent;
    }

    public void setResponseHeader(String key, String value)
    {
        _responseHeaders.put(key, value);
    }

    public Map<String, String> getResponseHeaders()
    {
        return _responseHeaders;
    }

    public boolean hasContent()
    {
        return _responseContent != null;
    }

    public boolean hasResponseWriter()
    {
        return _responseWriter != null;
    }

    public void setResponseWriter(ResponseWriter responseWriter)
    {
        _responseWriter = responseWriter;
    }

    public void writeResponse(OutputStream out)
        throws IOException
    {
        if(_responseContent != null)
            out.write(_responseContent);
        else if(_responseWriter != null)
            _responseWriter.writeResponse(out);
        out.flush();
    }

    public void addCookie(Cookie cookie) {
        _cookies.add(cookie);
    }

    public List<Cookie> getCookies() {
        return Collections.unmodifiableList(_cookies);
    }

    public void close() {
        // Subclasses can do request post-processing here.
    }

    //Static Helper methods

    public static WebResponse redirect(String location)
    {
        WebResponse webResponse = new WebResponse();
        webResponse.setHttpStatusCode(302);
        webResponse.setResponseHeader(WebConstants.LOCATION_HEADER, location);
        return webResponse;
    }

    public static WebResponse redirect(WebResponse response, String location)
    {
        response.setHttpStatusCode(302);
        response.setResponseHeader(WebConstants.LOCATION_HEADER, location);
        return response;
    }

    public static WebResponse ok(WebResponse response)
    {
        response.setHttpStatusCode(200);
        return response;
    }

    public static WebResponse badRequest(String content)
    {
        WebResponse webResponse = new WebResponse();
        webResponse.setHttpStatusCode(400);
        webResponse.setResponseContent(content.getBytes());
        return webResponse;
    }

    public static WebResponse badRequest(WebResponse response)
    {
        response.setHttpStatusCode(400);
        return response;
    }

    public static WebResponse notFound(String content)
    {
        WebResponse webResponse = new WebResponse();
        webResponse.setHttpStatusCode(404);
        webResponse.setResponseContent(content.getBytes());
        return webResponse;
    }

    public static WebResponse toJson(String key, String value)
    {
        Map<String, String> model = new HashMap<String, String>();
        model.put(key, value);
        return toJson(model);
    }

    public static WebResponse ok(String content)
    {
        WebResponse webResponse = new WebResponse();
        webResponse.setHttpStatusCode(200);
        webResponse.setResponseContent(content.getBytes());
        return webResponse;
    }

    public static WebResponse ok(ResponseWriter responseWriter)
    {
        WebResponse response = new WebResponse(200);
        response.setResponseWriter(responseWriter);
        return response;
    }

    public static WebResponse toJson(ResponseWriter responseWriter)
    {
        WebResponse response = new WebResponse();
        response.setHttpStatusCode(200);
        response.setContentType(WebConstants.CONTENT_TYPE_JSON);
        response.setResponseWriter(responseWriter);
        return response;
    }

    public static WebResponse toJson(final Object obj, int httpResponseCode)
    {
        WebResponse response = new WebResponse();
        response.setHttpStatusCode(httpResponseCode);
        response.setContentType(WebConstants.CONTENT_TYPE_JSON);
        ResponseWriter responseWriter = new ResponseWriter() {
                public void writeResponse(OutputStream out)
                    throws IOException {
                    OBJECT_MAPPER.writeValue(out, obj);
                }
            };
        response.setResponseWriter(responseWriter);
        return response;
    }

    public static WebResponse jsonError(JsonError jsonError)
    {
        int httpStatusCode = jsonError.getHttpStatusCode();
        if(httpStatusCode == -1)
            httpStatusCode = 400;
        return toJson(jsonError, httpStatusCode);
    }

    public static WebResponse toJson(final Object obj)
    {
        return toJson(obj, 200);
    }
}
