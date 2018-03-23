package com.distelli.webserver;

import com.distelli.utils.CompactUUID;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.io.InputStream;
import java.util.Scanner;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RequestHandler<R extends RequestContext>
{
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

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

    public String generateErrorId() {
        // Run `md5 -s $(hostname)` to determine which host this is
        return "[md5:" + md5(getServerName()) + " " +
            LocalDateTime.now(ZoneOffset.UTC).format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
            " " +
            CompactUUID.randomUUID() +
            "]";
    }

    private static String getServerName() {
        String hostname = null;
        if ( System.getProperty("os.name").startsWith("Windows") ) {
            hostname = System.getenv("COMPUTERNAME");
        } else {
            hostname = System.getenv("HOSTNAME");
        }
        if ( null != hostname ) return hostname;
        try {
            Process proc = Runtime.getRuntime().exec("hostname");
            try ( InputStream stream = proc.getInputStream() ) {
                try ( Scanner scanner = new Scanner(stream).useDelimiter("\\A") ) {
                    String str = scanner.hasNext() ? scanner.next() : "";
                    str = str.trim();
                    if ( str.isEmpty() ) return null;
                    return str;
                }
            }
        } catch ( IOException ex ) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    private static String md5(String str) {
        if ( null == str ) return null;
        try {
            return printHexBinary(MessageDigest.getInstance("MD5").digest(str.getBytes(UTF_8))).toLowerCase();
        } catch ( java.security.GeneralSecurityException ex ) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }
}
