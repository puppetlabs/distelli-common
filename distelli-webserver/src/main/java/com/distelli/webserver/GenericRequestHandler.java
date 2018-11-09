package com.distelli.webserver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

@FunctionalInterface
public interface GenericRequestHandler {
    /**
     * Called when standard HTTPMethod is made.
     *
     * {@link javax.servlet.http.HttpServlet#service(HttpServletRequest, HttpServletResponse)}
     *
     * @param request servlet request.
     *
     * @param response servlet response.
     */
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException;

    /**
     * Called when HTTP upgrade request is made (aka HTTPMethod.WEBSOCKET).
     *
     * {@link  org.eclipse.jetty.websocket.servlet.WebSocketCreator#createWebSocket(ServletUpgradeRequest,ServletUpgradeResponse)}
     *
     * @param request servlet request.
     *
     * @param response servlet response.
     */
    public default Object createWebSocketAdapter(ServletUpgradeRequest request, ServletUpgradeResponse response) {
        return null;
    }
}
