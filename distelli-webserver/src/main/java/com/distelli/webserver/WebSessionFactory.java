/*
  $Id: $
  @file WebSessionFactory.java
  @brief Contains the WebSessionFactory.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.security.Key;
import com.distelli.webserver.WebSession;

public abstract class WebSessionFactory<R extends RequestContext>
{
    protected Key _sessionKey = null;
    protected long _maxInactiveTimeMillis = 24*60*60*1000;
    protected boolean _httpOnly = true;
    protected boolean _isSecure = false;

    public WebSessionFactory()
    {

    }

    public abstract String getCookieName();

    public WebSession createSession()
    {
        return createSession(false, null);
    }

    public WebSession createSession(boolean loggedIn)
    {
        return createSession(loggedIn, null);
    }

    public WebSession createSession(boolean loggedIn, Map<String, String> vars)
    {
        WebSession.Builder webSessionBuilder = new WebSession.Builder()
        .withIsSecure(_isSecure)
        .withIsHttpOnly(_httpOnly)
        .withMaxInactiveTimeMillis(_maxInactiveTimeMillis)
        .withCookieName(getCookieName())
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .withSessionKey(_sessionKey)
        .withLoggedIn(loggedIn);
        if(vars != null)
            webSessionBuilder.withVars(vars);
        WebSession webSession = webSessionBuilder.build();
        return webSession;
    }

    public void logout(R requestContext)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null)
            webSession = createSession(false);
        else {
            webSession = new WebSession.Builder()
            .withSession(webSession)
            .buildFromSession(false);
        }

        requestContext.setWebSession(webSession);
    }

    public void updateSession(R requestContext, String var, String value)
    {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put(var, value);
        updateSession(requestContext, vars);
    }

    public void updateSession(R requestContext, Map<String, String> vars)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null) {
            webSession = createSession(false, vars);
            requestContext.setWebSession(webSession);
        } else {
            webSession = new WebSession.Builder()
            .withSession(webSession)
            .buildFromSession(vars);
        }
        requestContext.setWebSession(webSession);
    }

    public void login(R requestContext, Map<String, String> vars)
    {
        WebSession webSession = requestContext.getWebSession();
        if(webSession == null) {
            webSession = createSession(true, vars);
        } else {
            webSession = new WebSession.Builder()
            .withSession(webSession)
            .buildFromSession(true, vars);
        }

        requestContext.setWebSession(webSession);
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
        .withCookie(sessionCookie)
        .withIsSecure(_isSecure)
        .withIsHttpOnly(_httpOnly)
        .withMaxInactiveTimeMillis(_maxInactiveTimeMillis)
        .withCookieName(getCookieName())
        .withSessionKey(_sessionKey)
        .withLastActiveTimeMillis(System.currentTimeMillis())
        .buildFromCookie();

        return webSession;
    }
}
