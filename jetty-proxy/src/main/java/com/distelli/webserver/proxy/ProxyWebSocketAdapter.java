package com.distelli.webserver.proxy;

import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.Session;
import javax.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import java.net.URI;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class ProxyWebSocketAdapter extends WebSocketAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyWebSocketAdapter.class);

    public interface Factory {
        public ProxyWebSocketAdapter create(ProxyConfig config, ServletUpgradeRequest request, ServletUpgradeResponse response);
    }

    private WebSocketClient _client = new WebSocketClient();
    private Session _servletSession = null;
    private CountDownLatch _servletSessionLatch = new CountDownLatch(1);
    private Session _clientSession = null;
    private CountDownLatch _clientSessionLatch = new CountDownLatch(1);
    private ProxyConfig _config;

    private class ClientWebSocketAdapter implements WebSocketListener {
        @Override
        public void onWebSocketConnect(Session session) {
            _clientSession = session;
            _clientSessionLatch.countDown();
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            try {
                _servletSessionLatch.await();
                _servletSession.close(statusCode, reason);
            } catch ( RuntimeException ex ) {
                throw ex;
            } catch ( Exception ex ) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onWebSocketError(Throwable error) {
            if ( error instanceof java.util.concurrent.TimeoutException ) {
                LOG.debug(error.getMessage(), error);
            } else {
                LOG.error(error.getMessage(), error);
            }
            try {
                _servletSessionLatch.await();
                _servletSession.close(500, error.getMessage());
            } catch ( RuntimeException ex ) {
                throw ex;
            } catch ( Exception ex ) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onWebSocketText(String message) {
            try {
                _servletSessionLatch.await();
                _servletSession.getRemote().sendString(message);
            } catch ( RuntimeException ex ) {
                throw ex;
            } catch ( Exception ex ) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            try {
                _servletSessionLatch.await();
                _servletSession.getRemote().sendBytes(
                    ByteBuffer.wrap(payload, offset, len));
            } catch ( RuntimeException ex ) {
                throw ex;
            } catch ( Exception ex ) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Inject
    protected ProxyWebSocketAdapter(
        @Assisted ProxyConfig config,
        @Assisted ServletUpgradeRequest request,
        @Assisted ServletUpgradeResponse response)
    {
        try {
            _config = config;
            _client.start();
            URI uri = _config.getProxyTo().resolve(request.getRequestPath());
            String scheme = "ws";
            if ( null != uri.getScheme() ) {
                switch ( uri.getScheme().toLowerCase() ) {
                case "wss":
                case "https":
                    scheme = "wss";
                }
            }
            uri = new URI(
                scheme,
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment());
            _client.connect(
                new ClientWebSocketAdapter(),
                uri,
                toClientUpgradeRequest(request, uri));
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        _servletSession = session;
        _servletSessionLatch.countDown();
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        try {
            _clientSessionLatch.await();
            _clientSession.close(statusCode, reason);
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onWebSocketError(Throwable error) {
        LOG.error(error.getMessage(), error);
        try {
            _clientSessionLatch.await();
            _clientSession.close(500, error.getMessage());
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            _clientSessionLatch.await();
            _clientSession.getRemote().sendString(message);
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        try {
            _clientSessionLatch.await();
            _clientSession.getRemote().sendBytes(
                ByteBuffer.wrap(payload, offset, len));
        } catch ( RuntimeException ex ) {
            throw ex;
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

    private ClientUpgradeRequest toClientUpgradeRequest(ServletUpgradeRequest src, URI uri) {
        ClientUpgradeRequest dst = new ClientUpgradeRequest();
        dst.setRequestURI(uri);
        dst.setHeaders(sanitizedHeaders(src));
        return dst;
    }

    private static final Set<String> REMOVE_HEADERS = new HashSet<String>() {{
            add("sec-websocket-key");
            add("sec-websocket-version");
            add("via");
            add("accept-encoding");
            add("user-agent");
            add("cache-control");
            add("connection");
            add("pragma");
            add("upgrade");
            add("x-forwarded-proto");
            add("x-forwarded-host");
        }};

    private static Map<String, List<String>> sanitizedHeaders(ServletUpgradeRequest src) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for ( Map.Entry<String, List<String>> entry : src.getHeaders().entrySet() ) {
            if ( null == entry.getKey() ) continue;
            if ( REMOVE_HEADERS.contains(entry.getKey().toLowerCase()) ) continue;
            headers.put(entry.getKey(), entry.getValue());
        }
        if ( null == src.getHeader("X-Forwarded-Proto") ) {
            headers.put("X-Forwarded-Proto", Collections.singletonList(src.isSecure() ? "wss" : "ws"));
        }
        if ( null == src.getHeader("X-Forwarded-Host") ) {
            headers.put("X-Forwarded-Host", Collections.singletonList(src.getHost()));
        }
        String via = src.getHeader("Via");
        via = ( null == via ) ? getVia(src) : via + ", " + getVia(src);
        return headers;
    }

    private static String getVia(ServletUpgradeRequest src) {
        StringBuilder sb = new StringBuilder();
        sb.append(src.isSecure() ? "wss" : "ws");
        sb.append("/");
        sb.append(src.getProtocolVersion());
        sb.append(" ");
        sb.append(src.getHost());
        return sb.toString();
    }
}
