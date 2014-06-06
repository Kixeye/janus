/*
 * #%L
 * Janus
 * %%
 * Copyright (C) 2014 KIXEYE, Inc
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.kixeye.core.janus.client.websocket;


import com.google.common.base.Preconditions;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.Janus;
import com.kixeye.core.janus.ServerInstance;
import com.kixeye.core.janus.client.exception.NoServerAvailableException;
import com.kixeye.core.janus.client.exception.RetriesExceededException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * This class is used to establish a {@link WebSocketSession} with a remote websocket endpoint.
 * It uses {@link Janus} to discover and connect to the appropriate server instance and track server
 * usage statistics for the server being connected to.
 * <p/>
 * A single instance of this class can be used create multiple {@link WebSocketSession}s.
 *
 * @author cbarry@kixeye.com
 */
public class SessionWebSocketClient {

    private final Logger logger = LoggerFactory.getLogger(StatelessWebSocketClient.class);

    private final Janus janus;
    private final int numRetries;
    private final String relativeUrl;
    private final WebSocketClient webSocketClient;

    /**
     * @param janus           the {@link Janus} instances managing the service cluster
     * @param numRetries      the maximum number of retries that while attempting to connect to the remote endpoint
     * @param relativeUrl     the relative url (path) that the remote websocket endpoint is listening on
     * @param webSocketClient the underlying {@link WebSocketClient} which will be used to communicate with the remote endpoint
     */
    public SessionWebSocketClient(Janus janus, int numRetries, String relativeUrl, WebSocketClient webSocketClient) {
        Preconditions.checkNotNull(janus, "'janus' cannot be null.");
        Preconditions.checkArgument(numRetries >= 0, "'numberRetries must be >= 0'");
        Preconditions.checkNotNull(webSocketClient, "'webSocketClient' cannot be null");

        this.janus = janus;
        this.numRetries = numRetries;
        this.relativeUrl = relativeUrl == null ? "" : relativeUrl.trim();
        this.webSocketClient = webSocketClient;

        try {
            this.webSocketClient.start();
        } catch (Exception e) {
            logger.error("Unable to start WebSocketClient", e);
        }
    }

    /**
     * Establishes a new {@link WebSocketSession} with a server instance
     * selected by {@link Janus}.
     *
     * @param listener a {@link WebSocketListener} used to listen for session events from the created {@link WebSocketSession}
     * @return {@link WebSocketSession}
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
    public WebSocketSession getNewSession(WebSocketListener listener) throws NoServerAvailableException, RetriesExceededException {
        Preconditions.checkNotNull(listener, "'listener' cannot be null.");

        long retries = numRetries;
        do {
            // get a load balanced server
            ServerStats server = janus.getServer();
            if (server == null) {
                throw new NoServerAvailableException(janus.getServiceName());
            }

            // try to create a new session
            ServerInstance instance = server.getServerInstance();
            String newUrl = (instance.isSecure() ? "wss://" : "ws://") + instance.getHost() + ":" + instance.getWebsocketPort() + relativeUrl;
            Session session = null;
            try {
                session = webSocketClient.connect(new ProxyWebSocketListener(server, listener), new URI(newUrl)).get(1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.debug("Received connection exception, retrying another server", e);
                server.incrementErrors();
            }

            // wrap session and return to caller
            if (session != null) {
                return new JettyWebSocketSession(server, session);
            }

            retries -= 1;
        } while (retries >= 0);

        throw new RetriesExceededException(janus.getServiceName(), numRetries);
    }

    /**
     * Wrap Jetty's WebSocketListener so we can multiplex the
     * different connections into a single listener.
     */
    private class ProxyWebSocketListener implements WebSocketListener {

        final private ServerStats server;
        final private WebSocketListener listener;

        public ProxyWebSocketListener(ServerStats server, WebSocketListener listener) {
            this.server = server;
            this.listener = listener;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            server.incrementReceivedMessages();
            listener.onWebSocketBinary(payload, offset, len);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            server.decrementOpenSessions();
            listener.onWebSocketClose(statusCode, reason);
        }

        @Override
        public void onWebSocketConnect(Session session) {
            // caller shouldn't be using this session, so hide it from them
            server.incrementOpenSessions();
            listener.onWebSocketConnect(null);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            server.incrementErrors();
            listener.onWebSocketError(cause);
        }

        @Override
        public void onWebSocketText(String message) {
            server.incrementReceivedMessages();
            listener.onWebSocketText(message);
        }
    }
}
