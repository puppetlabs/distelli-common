/*
  $Id: $
  @file WebSession.java
  @brief Contains the WebSession.java class

  @author Rahul Singh [rsingh]
  @author Brian Maher [bmaher]
*/
package com.distelli.webserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;

public class WebSession
{
    private static final Logger log = LoggerFactory.getLogger(WebServlet.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());
    static {
        // Support deserializing interfaces:
        OBJECT_MAPPER.registerModule(new MrBeanModule());
    }

    private static final long   MAX_CLOCK_SKEW_MILLIS = 60*60*1000;

    public static final String LOGGED_IN_KEY = "lgdn";
    public static final String LAST_ACTIVE_TIME = "lat";

    protected int sessionVersion = 1;
    protected boolean isSecure = false;
    protected boolean isHttpOnly = true;
    protected Key sessionKey;
    protected Map<String, String> vars;
    protected long maxInactiveTimeMillis;
    protected String cookieName;

    public static class Builder {
        private int _sessionVersion = 1;
        private Key _sessionKey;
        private boolean _isSecure = false;
        private boolean _isHttpOnly = true;
        private Map<String, String> _vars;
        private long _lastActiveTimeMillis = 0;
        private long _maxInactiveTimeMillis = 0;
        private String _cookieName;
        private boolean _isLoggedIn;

        private WebSession _session = null;
        private Cookie _cookie = null;
        public Builder()
        {
            this._vars = new HashMap<String, String>();
        }
        public Builder withIsSecure(boolean isSecure) {this._isSecure = isSecure; return this;}
        public Builder withIsHttpOnly(boolean isHttpOnly) {this._isHttpOnly = isHttpOnly; return this;}
        public Builder withVar(String key, String value) {this._vars.put(key, value); return this;}
        public Builder withVars(Map<String, String> vars) {
            checkForReservedVars(vars);
            this._vars.putAll(vars);
            return this;
        }
        public Builder withLastActiveTimeMillis(long lastActiveTimeMillis) {
            this._lastActiveTimeMillis = lastActiveTimeMillis; return this;
        }
        public Builder withMaxInactiveTimeMillis(long maxInactiveTimeMillis) {
            this._maxInactiveTimeMillis = maxInactiveTimeMillis; return this;
        }
        public Builder withCookieName(String cookieName) {this._cookieName = cookieName; return this;}
        public Builder withSessionKey(Key sessionKey) {this._sessionKey = sessionKey; return this;}
        public Builder withSessionVersion(int sessionVersion) {this._sessionVersion = sessionVersion; return this;}
        public Builder withLoggedIn(boolean isLoggedIn) {this._isLoggedIn = isLoggedIn; return this;}
        public Builder withSession(WebSession webSession) {this._session = webSession; return this;}
        public Builder withCookie(Cookie cookie) {this._cookie = cookie; return this;}

        private void checkForReservedVars(Map<String, String> vars) {
            if(vars == null)
                return;
            if(vars.containsKey(LOGGED_IN_KEY))
                throw(new IllegalArgumentException("Cannot set reserved Session Var: "+LOGGED_IN_KEY));
            if(vars.containsKey(LAST_ACTIVE_TIME))
                throw(new IllegalArgumentException("Cannot set reserved Session Var: "+LAST_ACTIVE_TIME));
        }

        public WebSession buildFromSession()
        {
            return buildFromSession(null);
        }

        public WebSession buildFromSession(Map<String, String> vars)
        {
            return buildFromSession(null, vars);
        }

        public WebSession buildFromSession(boolean isLoggedIn)
        {
            return buildFromSession(isLoggedIn, null);
        }

        //This build method ignores all the properties in the builder
        //and instead uses the _session. It creates a new session
        //thats exactly the same as the _session then it updates the
        //vars with the ones passed in. If the Boolean isLogged in is
        //non-null then the new session is updated with that
        public WebSession buildFromSession(Boolean isLoggedIn, Map<String, String> vars)
        {
            WebSession webSession = new WebSession();
            webSession.maxInactiveTimeMillis = _session.maxInactiveTimeMillis;
            webSession.cookieName = _session.cookieName;
            webSession.sessionVersion = _session.sessionVersion;
            webSession.sessionKey = _session.sessionKey;
            webSession.isHttpOnly = _session.isHttpOnly;
            webSession.isSecure = _session.isSecure;
            webSession.vars = _session.getVars();
            webSession.setLoggedIn(_session.isLoggedIn());
            webSession.setLastActiveTime(_session.getLastActiveTimeMillis());
            if(vars != null)
            {
                checkForReservedVars(vars);
                if(webSession.vars == null)
                    webSession.vars = new HashMap<String, String>();
                webSession.vars.putAll(vars);
            }
            if(webSession.vars == null)
                webSession.vars = new HashMap<String, String>();
            if(isLoggedIn != null)
                webSession.setLoggedIn(isLoggedIn.booleanValue());
            if(_lastActiveTimeMillis != 0)
                webSession.setLastActiveTime(_lastActiveTimeMillis);
            return webSession;
        }

        //This method gets the vars from the cookie.
        public WebSession buildFromCookie()
        {
            WebSession webSession = new WebSession();
            webSession.maxInactiveTimeMillis = _maxInactiveTimeMillis;
            webSession.cookieName = _cookieName;
            webSession.sessionVersion = _sessionVersion;
            webSession.sessionKey = _sessionKey;
            webSession.isHttpOnly = _isHttpOnly;
            webSession.isSecure = _isSecure;
            if(_cookie != null) {
                try {
                    webSession.vars = webSession.deserialize(_cookie.getValue());
                } catch(Throwable t) {
                    throw(new RuntimeException(t));
                }
            }
            if(webSession.vars == null)
                webSession.vars = new HashMap<String, String>();

            webSession.setLastActiveTime(_lastActiveTimeMillis);
            return webSession;
        }

        public WebSession build()
        {
            WebSession webSession = new WebSession();
            webSession.vars = _vars;
            webSession.maxInactiveTimeMillis = _maxInactiveTimeMillis;
            webSession.cookieName = _cookieName;
            webSession.sessionVersion = _sessionVersion;
            webSession.sessionKey = _sessionKey;
            webSession.isHttpOnly = _isHttpOnly;
            webSession.isSecure = _isSecure;
            webSession.setLoggedIn(_isLoggedIn);
            webSession.setLastActiveTime(_lastActiveTimeMillis);
            return webSession;
        }
    }

    public WebSession()
    {

    }

    public Map<String, String> getVars()
    {
        return this.vars;
    }

    public String getVar(String key)
    {
        if(this.vars == null)
            return null;
        return this.vars.get(key);
    }

    public boolean isLoggedIn()
    {
        if(this.vars == null)
            return false;
        String loggedIn = this.vars.get(LOGGED_IN_KEY);
        if(loggedIn == null)
            return false;
        return loggedIn.equalsIgnoreCase("1");
    }

    public int getSessionVersion()
    {
        return this.sessionVersion;
    }

    public boolean isSecure()
    {
        return this.isSecure;
    }

    public boolean isHttpOnly()
    {
        return this.isHttpOnly;
    }

    public Key getSessionKey()
    {
        return this.sessionKey;
    }

    public long getLastActiveTimeMillis()
    {
        return getLastActiveTimeLong();
    }

    public long getMaxInactiveTimeMillis()
    {
        return this.maxInactiveTimeMillis;
    }

    public String getCookieName()
    {
        return this.cookieName;
    }

    private long getLastActiveTimeLong()
    {
        if(this.vars == null)
            return 0;
        String latStr = this.vars.get(LAST_ACTIVE_TIME);
        if(latStr == null)
            return 0;
        try {
            return Long.parseLong(latStr);
        } catch(NumberFormatException nfe) {
            return 0;
        }
    }

    private void setLastActiveTime(long lastActiveTimeMillis)
    {
        if(this.vars == null)
            return;
        this.vars.put(LAST_ACTIVE_TIME, String.format("%d", lastActiveTimeMillis));
    }

    private void setLoggedIn(boolean loggedIn)
    {
        if(loggedIn)
            this.vars.put(LOGGED_IN_KEY, "1");
        else
            this.vars.put(LOGGED_IN_KEY, "0");
    }

    public boolean isExpired()
    {
        if(this.vars == null)
            return true;

        long now = System.currentTimeMillis();
        long lastActiveTimeMillis = getLastActiveTimeMillis();
        if(now < lastActiveTimeMillis - MAX_CLOCK_SKEW_MILLIS ) {
            log.error("lastActiveTime is a time in the future, check clock skew.");
            return true;
        }
        if(now > lastActiveTimeMillis+this.maxInactiveTimeMillis) {
            if(log.isDebugEnabled())
                log.debug("session expired");
            return true;
        }
        return false;
    }

    public Cookie toCookie()
    {
        try {
            String value;
            if(this.vars == null)
                value = "";
            else
                value = serialize(this.vars);
            Cookie cookie = new Cookie(this.cookieName, value);
            cookie.setPath("/");
            if(this.vars == null)
                cookie.setMaxAge(0);
            cookie.setSecure(this.isSecure);
            cookie.setHttpOnly(this.isHttpOnly);
            cookie.setVersion(this.sessionVersion);
            return cookie;
        } catch(Throwable t) {
            throw(new RuntimeException(t));
        }
    }

    private String serialize(Map<String, String> vars)
        throws JsonProcessingException,
               NoSuchAlgorithmException,
               InvalidKeyException,
               IllegalBlockSizeException,
               IOException,
               NoSuchPaddingException,
               BadPaddingException
    {
        if(vars == null)
            return "";
        byte[] text = OBJECT_MAPPER.writeValueAsBytes(vars);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(this.sessionVersion); // Version_1byte

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, this.sessionKey);
        byte[] ciphertext = cipher.doFinal(text);
        outStream.write(cipher.getIV()); // Iv_16byte(random)
        outStream.write(ciphertext); // AES(raw);

        Mac sha256 = Mac.getInstance("HmacSHA256");
        sha256.init(this.sessionKey);
        outStream.write(sha256.doFinal(outStream.toByteArray())); //SHA256_32byte

        byte[] output = outStream.toByteArray();
        return Base64.getUrlEncoder().encodeToString(output);
    }

    private Map<String, String> deserialize(String value)
        throws NoSuchAlgorithmException,
               InvalidKeyException,
               IllegalBlockSizeException,
               IOException,
               NoSuchPaddingException,
               InvalidAlgorithmParameterException,
               BadPaddingException
    {
        if(value == null || value.trim().isEmpty())
            return null;
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if(bytes == null || bytes.length == 0)
            return null;
        if(bytes.length < 49) {
            if(log.isDebugEnabled())
                log.debug("decodeBase64 returned "+bytes.length+" which is less than 49");
            return null;
        }
        if(bytes[0] != this.sessionVersion) {
            if(log.isDebugEnabled())
                log.debug("unknown session version: "+bytes[0]);
            return null;
        }
        Mac sha256 = Mac.getInstance("HmacSHA256");
        sha256.init(this.sessionKey);
        sha256.update(bytes, 0, bytes.length-32);
        byte[] hmacShaBytes = sha256.doFinal();
        byte[] macData = Arrays.copyOfRange(bytes, bytes.length-32, bytes.length);
        if(!isEqual(macData, hmacShaBytes)) {
            if(log.isDebugEnabled())
                log.debug("mac check failed");
            return null;
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, this.sessionKey, new IvParameterSpec(bytes, 1, 16));
        byte[] ciphertext = Arrays.copyOfRange(bytes, 17, bytes.length-32);
        byte[] text = cipher.doFinal(ciphertext);
        return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, String>>(){});
    }

    private boolean isEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
