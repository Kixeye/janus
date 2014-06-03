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
import com.kixeye.core.janus.Janus;
import com.kixeye.core.janus.ServerInstance;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.client.exception.NoServerAvailableException;
import com.kixeye.core.janus.client.exception.RetriesExceededException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *  A websocket client that send messages to the a remote endpoint chosen by {@link Janus}.  The client
 *  is considered stateless in that there is no guarantee which remote server instance will receive the
 *  message and therefore any remote server instance should be able to service the message.
 *
 *  This client maintains a pool of sessions to established with remote server instances and will re-use
 *  sessions from this pool if available. If no session is available for the selected server instance, a new
 *  session will be established with the server instance and added to the pool.
 *
 * @author cbarry@kixeye.com
 */
public class StatelessWebSocketClient<X extends ServerStats, Y extends ServerInstance> implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(StatelessWebSocketClient.class);

    private final Janus<X,Y> janus;
    private final int numRetries;
    private final String relativeUrl;
    private final WebSocketListener listener;
    private final Map<X,Session> sessions = new ConcurrentHashMap<X,Session>();
    private final Map<Session,Object> sessionLocks = new ConcurrentHashMap<Session,Object>();
    private final WebSocketClient webSocketClient;

    /**
     * Creates a StatelessWebSocketClient
     * @param janus the {@link Janus} instances managing the service cluster
     * @param numRetries the maximum number of retries that while attempting to connect to the remote endpoint
     * @param relativeUrl the relative url (path) that the remote websocket endpoint is listening on
     * @param webSocketClient the underlying {@link WebSocketClient} which will be used to communicate with the remote endpoint
     * @param listener a listener for responding message events
     */
    public StatelessWebSocketClient(Janus<X, Y> janus, int numRetries, String relativeUrl, WebSocketClient webSocketClient, WebSocketListener listener) {
        Preconditions.checkNotNull(janus, "'janus' cannot be null.");
        Preconditions.checkArgument(numRetries >= 0, "'numberRetries must be >= 0'");
        Preconditions.checkNotNull(webSocketClient, "'webSocketClient' cannot be null");
        Preconditions.checkNotNull(listener, "'listener' cannot be null");

        this.janus = janus;
        this.numRetries = numRetries;
        this.relativeUrl = relativeUrl;
        this.listener = listener;
        this.webSocketClient = webSocketClient;
        try {
            this.webSocketClient.start();
        } catch (Exception e) {
            logger.error("Unable to start WebSocketClient", e);
        }
    }

    /**
     * Write the given bytes to the remote endpoint, blocking until all the
     * bytes have been transmitted.
     *
     * @param data the bytes to send
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
    public void sendBytes(final ByteBuffer data) throws NoServerAvailableException, RetriesExceededException {
        Preconditions.checkNotNull(data, "'data' cannot be null");

        SendWrapper<X> wrapped = new SendWrapper<X>() {
            @Override
            public void execute(final Session session, X server) throws IOException {
                synchronized (sessionLocks.get(session)) {
                    session.getRemote().sendBytes(data);
                }
            }
        };
        sendWithLoadBalancer(wrapped);
    }

    /**
     * Write the given bytes to the remote endpoint asynchronously. Clients can
     * provide a callback to be notified when the bytes have been transmitted (or failed to transmit).
     *
     * @param data the bytes to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
    public void sendBytes(final ByteBuffer data, final WriteCallback callback) throws NoServerAvailableException, RetriesExceededException {
        Preconditions.checkNotNull(data, "'data' cannot be null");

        final ProxyWriteCallback proxy = new ProxyWriteCallback(callback);
        SendWrapper<X> wrapped = new SendWrapper<X>() {
            @Override
            public void execute(Session session, X server) throws IOException {
                proxy.setServer(server);
                session.getRemote().sendBytes(data, proxy);
            }
        };
        sendWithLoadBalancer(wrapped);
    }

    /**
     * Write the text to the remote endpoint, blocking until all the
     * data has been transmitted.
     *
     * @param text the text to send
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
    public void sendString(final String text) throws NoServerAvailableException, RetriesExceededException {
        Preconditions.checkNotNull(text, "'text' cannot be null");

        SendWrapper<X> wrapped = new SendWrapper<X>() {
            @Override
            public void execute(Session session, X server) throws IOException {
                session.getRemote().sendString(text);
            }
        };
        sendWithLoadBalancer(wrapped);
    }

    /**
     * Write the given text to the remote endpoint asynchronously. Clients can
     * provide a callback to be notified when the text has been transmitted (or failed to transmit).
     *
     * @param text the text to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
    public void sendString(final String text, final WriteCallback callback) throws NoServerAvailableException, RetriesExceededException {
        Preconditions.checkNotNull(text, "'text' cannot be null");

        final ProxyWriteCallback proxy = new ProxyWriteCallback(callback);
        SendWrapper<X> wrapped = new SendWrapper<X>() {
            @Override
            public void execute(Session session, X server) throws IOException {
                proxy.setServer(server);
                session.getRemote().sendString(text, proxy);
            }
        };
        sendWithLoadBalancer(wrapped);
    }

    /**
     * Close the session
     */
    public void close() {
    	for (Session session : sessions.values()) {
    		try {
    			session.close();
    		} catch (Exception e) {
    			logger.error("Unable to close session", e);
    		}
    	}
    }

    private void sendWithLoadBalancer(SendWrapper<X> function) throws NoServerAvailableException, RetriesExceededException {
        long retries = numRetries;
        do {
            // get a load balanced server
            X server = janus.getServer();
            if (server == null) {
                throw new NoServerAvailableException( janus.getServiceName() );
            }

            // does this server already have an open session?
            Session session = sessions.get(server);
            if (session != null) {
                // if no longer valid, remove it and try again without
                // counting as a retry since not really a failure
                if (!session.isOpen()) {
                    sessionLocks.remove(session);
                    sessions.remove(server);
                    continue;
                }
            } else {
                // no session to this server so create one
                ServerInstance instance = server.getServerInstance();
                String newUrl = (instance.isSecure() ? "wss://" : "ws://") + instance.getHost() + ":" + instance.getWebsocketPort() + relativeUrl;
                try {
                    session = webSocketClient.connect(new ProxyWebSocketListener(server), new URI(newUrl)).get(1000, TimeUnit.MILLISECONDS);
                    sessions.put(server,session);
                    sessionLocks.put(session, new Object());
                } catch (Exception e) {
                    logger.debug("Received connection exception, retrying another server", e);
                    server.incrementErrors();
                    session = null;
                }
            }

            // send the message
            if (session != null) {
                try {
                    server.incrementSentMessages();
                    function.execute(session, server);
                    return;
                } catch (IOException e) {
                    logger.debug("Received send exception, retrying another server", e);
                    server.incrementErrors();
                    sessionLocks.remove(session);
                    sessions.remove(server);
                }
            }

            retries -= 1;
        } while (retries >= 0);

        throw new RetriesExceededException(janus.getServiceName(),numRetries);
    }

    /***
     * Wrap the send functions so can share the connect and retry logic.
     */
    private interface SendWrapper<X> {
        void execute(Session session,X server) throws IOException;
    }

    /***
     * Wrap Jetty's WebSocketListener so we can multiplex the
     * different connections into a single listener.
     */
    private class ProxyWebSocketListener implements WebSocketListener {

        final private X server;

        public ProxyWebSocketListener(X server) {
            this.server = server;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            server.incrementReceivedMessages();
            listener.onWebSocketBinary(payload,offset,len);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            server.decrementOpenSessions();
            sessions.remove(server);
        }

        @Override
        public void onWebSocketConnect(Session session) {
            server.incrementOpenSessions();
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            server.incrementErrors();
        }

        @Override
        public void onWebSocketText(String message) {
            server.incrementReceivedMessages();
            listener.onWebSocketText(message);
        }
    }

    /***
     * Wrap Jetty's WriteCallback so we can increment the error count.
     */
    private class ProxyWriteCallback implements WriteCallback {

        final private WriteCallback callback;
        private X server;

        private ProxyWriteCallback(WriteCallback callback) {
            this.callback = callback;
        }

        public void setServer(X server) {
            this.server = server;
        }

        @Override
        public void writeFailed(Throwable x) {
            server.incrementErrors();
            if(callback != null){
                callback.writeFailed(x);
            }
        }

        @Override
        public void writeSuccess() {
            if(callback != null){
                callback.writeSuccess();
            }
        }
    }
}
