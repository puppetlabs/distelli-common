/*
  $Id: $
  @file TestWebSession.java
  @brief Contains the TestWebSession.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.webserver;

import java.util.Arrays;
import java.util.HashMap;

import java.util.Map;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import javax.servlet.http.Cookie;
import java.security.Key;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.Assert.*;

public class TestWebSession
{
    private static final String COOKIE_NAME = "com.example.sid";
    private static final long   MAX_INACTIVE_TIME_MILLIS = 24*60*60*1000;
    private static Key sessionKey = null;
    static {
        try {
            String sessionKeyStr = "plVUQxredioisb13XRjJfA==";
            byte[] sessionKeyData = Base64.getDecoder().decode(sessionKeyStr);
            sessionKey = new SecretKeySpec(sessionKeyData, "AES");
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testNewWebSession()
    {
        String sessionId = "1234567890";
        WebSession webSession = new WebSession.Builder()
        .withIsSecure(true)
        .withIsHttpOnly(true)
        .withLoggedIn(true)
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .withMaxInactiveTimeMillis(MAX_INACTIVE_TIME_MILLIS)
        .withCookieName(COOKIE_NAME)
        .withSessionKey(sessionKey)
        .withVar("A", "B")
        .withVar("C", "D")
        .build();

        assertThat(webSession, is(not(nullValue())));
        assertThat(webSession.getVar("A"), is(not(nullValue())));
        assertThat(webSession.getVar("A"), equalTo("B"));
        assertThat(webSession.getVar("C"), is(not(nullValue())));
        assertThat(webSession.getVar("C"), equalTo("D"));
        assertThat(webSession.isLoggedIn(), equalTo(true));
        assertThat(webSession.isExpired(), equalTo(false));


        Cookie cookie = webSession.toCookie();

        WebSession webSession2 = new WebSession.Builder()
        .withIsSecure(true)
        .withIsHttpOnly(true)
        .withMaxInactiveTimeMillis(MAX_INACTIVE_TIME_MILLIS)
        .withCookieName(COOKIE_NAME)
        .withSessionKey(sessionKey)
        .withCookie(cookie)
        .withLastActiveTimeMillis(webSession.getLastActiveTimeMillis())
        .buildFromCookie();

        assertThat(webSession2, is(not(nullValue())));
        assertThat(webSession2.getVar("A"), is(not(nullValue())));
        assertThat(webSession2.getVar("A"), equalTo("B"));
        assertThat(webSession2.getVar("C"), is(not(nullValue())));
        assertThat(webSession2.getVar("C"), equalTo("D"));
        assertThat(webSession2.isLoggedIn(), equalTo(true));

        assertThat(webSession.getSessionVersion(), equalTo(webSession2.getSessionVersion()));
        assertThat(webSession.isSecure(), equalTo(webSession2.isSecure()));
        assertThat(webSession.isHttpOnly(), equalTo(webSession2.isHttpOnly()));
        assertThat(webSession.getSessionKey(), equalTo(webSession2.getSessionKey()));
        assertThat(webSession.getLastActiveTimeMillis(), equalTo(webSession2.getLastActiveTimeMillis()));
        assertThat(webSession.getMaxInactiveTimeMillis(), equalTo(webSession2.getMaxInactiveTimeMillis()));
        assertThat(webSession.getCookieName(), equalTo(webSession2.getCookieName()));
        assertThat(webSession.getVars(), equalTo(webSession2.getVars()));

        assertThat(webSession2.isExpired(), equalTo(false));
    }

    @Test
    public void testLogin()
    {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("A", "B");
        WebSession.Builder webSessionBuilder = new WebSession.Builder()
        .withIsSecure(false)
        .withIsHttpOnly(true)
        .withMaxInactiveTimeMillis(MAX_INACTIVE_TIME_MILLIS)
        .withCookieName(COOKIE_NAME)
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .withSessionKey(sessionKey)
        .withLoggedIn(true);
        if(vars != null)
            webSessionBuilder.withVars(vars);
        WebSession webSession = webSessionBuilder.build();
    }
}
