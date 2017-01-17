/*
  $Id: $
  @file WebSessionFactory.java
  @brief Contains the WebSessionFactory.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.security.Key;
import com.distelli.webserver.WebSession;

public abstract class WebSessionFactory<R extends RequestContext>
{
    protected Key _sessionKey = null;
    protected long _maxInactiveTimeMillis = 24*60*60*1000;

    public WebSessionFactory()
    {

    }

    public abstract String getCookieName();

    public WebSession createSession()
    {
        return createSession(false);
    }

    public WebSession createSession(boolean loggedIn)
    {
        WebSession webSession = new WebSession.Builder()
        .withIsSecure(false)
        .withIsHttpOnly(true)
        .withMaxInactiveTimeMillis(_maxInactiveTimeMillis)
        .withCookieName(getCookieName())
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .withSessionKey(_sessionKey)
        .withLoggedIn(loggedIn)
        .build();
        return webSession;
    }

    public void logout(R requestContext)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null) {
            webSession = createSession();
            requestContext.setWebSession(webSession);
            return;
        }
        webSession = new WebSession.Builder()
        .withLoggedIn(false)
        .buildFromSession(webSession);
        requestContext.setWebSession(webSession);
        return;
    }

    public void login(R requestContext)
    {
        WebSession webSession = createSession(true);
        requestContext.setWebSession(webSession);
        return;
    }

    public boolean isLoggedIn(R requestContext)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null)
            return false;
        return webSession.isLoggedIn();
    }

    public boolean isExpired(R requestContext)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null)
            return true;
        return webSession.isExpired();
    }

    public boolean shouldLogin(R requestContext)
    {
        WebSession webSession = requestContext.getWebSession();
        return shouldLogin(webSession);
    }

    public boolean shouldLogin(WebSession webSession)
    {
        if(webSession == null)
            return true;
        if(webSession.isExpired())
            return true;
        if(!webSession.isLoggedIn())
            return true;
        return false;
    }

    public WebSession fromRequestContext(R requestContext)
    {
        Cookie[] cookies = requestContext.getCookies();
        if(cookies == null)
            return null;
        HttpSession httpSession = requestContext.getSession();
        if(httpSession == null)
            return null;
        String sessionId = httpSession.getId();
        if(sessionId == null)
            return null;
        //check for null cookies
        Cookie sessionCookie = null;
        for(Cookie cookie : cookies)
        {
            if(cookie.getName().equalsIgnoreCase(getCookieName()))
            {
                sessionCookie = cookie;
                break;
            }
        }
        requestContext.setSessionCookie(sessionCookie);
        if(sessionCookie == null)
            return null;
        WebSession webSession = new WebSession.Builder()
        .withSessionId(sessionId)
        .withIsSecure(true)
        .withIsHttpOnly(true)
        .withMaxInactiveTimeMillis(_maxInactiveTimeMillis)
        .withCookieName(getCookieName())
        .withSessionKey(_sessionKey)
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .buildFromCookie(sessionCookie);

        return webSession;
    }
}
