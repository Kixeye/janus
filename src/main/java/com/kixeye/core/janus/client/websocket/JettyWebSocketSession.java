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
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of {@link WebSocketSession} which wraps Jetty's {@link Session} and adds
 * tracking of statistics about the messages sent to the remote endpoint. It is expected that
 * given {@link com.kixeye.core.janus.ServerStats} object corresponds to the remote endpoint represented by the given
 * {@link Session}.
 *
 * @author cbarry@kixeye.com
 */
public class JettyWebSocketSession implements WebSocketSession {

    private final ServerStats stats;
    private final Session session;

    public JettyWebSocketSession(ServerStats stats, Session session) {
        this.stats = Preconditions.checkNotNull(stats, "'stats' cannot be null.");
        this.session = Preconditions.checkNotNull(session, "'session' cannot be null.");
    }

    /**
     * @see {@link WebSocketSession#close()}
     */
    @Override
    public void close() {
        session.close();
    }

    /**
     * @return whether or not the session is open
     * @see {@link WebSocketSession#isOpen()}
     */
    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    /**
     * @param data the bytes to send
     * @throws IOException
     * @see {@link WebSocketSession#sendBytes(java.nio.ByteBuffer)}
     */
    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        //TODO catch exception, increment error count, re-throw
        stats.incrementSentMessages();
        session.getRemote().sendBytes(data);
    }

    /**
     * @param data the bytes to send
     * @return {@link Future}
     * @see {@link WebSocketSession#sendBytesByFuture(java.nio.ByteBuffer)}
     */
    @Override
    public Future<Void> sendBytesByFuture(ByteBuffer data) {
        stats.incrementSentMessages();
        Future<Void> future = session.getRemote().sendBytesByFuture(data);
        return new ProxyFuture<>(future);
    }

    /**
     * @param data     the bytes to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     * @see {@link WebSocketSession#sendBytes(java.nio.ByteBuffer, org.eclipse.jetty.websocket.api.WriteCallback)}
     */
    @Override
    public void sendBytes(ByteBuffer data, WriteCallback callback) {
        ProxyWriteCallback proxy = new ProxyWriteCallback(callback);
        stats.incrementSentMessages();
        session.getRemote().sendBytes(data, proxy);
    }

    /**
     * @param text the text to send
     * @throws IOException
     * @see {@link WebSocketSession#sendString(String)}
     */
    @Override
    public void sendString(String text) throws IOException {
        //TODO catch exception, increment error count, re-throw
        stats.incrementSentMessages();
        session.getRemote().sendString(text);
    }

    /**
     * @param text the text to send
     * @return {@link Future}
     * @see {@link WebSocketSession#sendStringByFuture(String)}
     */
    @Override
    public Future<Void> sendStringByFuture(String text) {
        stats.incrementSentMessages();
        Future<Void> future = session.getRemote().sendStringByFuture(text);
        return new ProxyFuture<>(future);
    }

    /**
     * @param text     the text to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     * @see {@link WebSocketSession#sendString(String, org.eclipse.jetty.websocket.api.WriteCallback)}
     */
    @Override
    public void sendString(String text, WriteCallback callback) {
        ProxyWriteCallback proxy = new ProxyWriteCallback(callback);
        stats.incrementSentMessages();
        session.getRemote().sendString(text, proxy);
    }

    /**
     * Wrap WriteCallback so we can increment the error count.
     */
    private class ProxyWriteCallback implements WriteCallback {

        final private WriteCallback callback;

        private ProxyWriteCallback(WriteCallback callback) {
            this.callback = callback;
        }

        @Override
        public void writeFailed(Throwable x) {
            stats.incrementErrors();
            callback.writeFailed(x);
        }

        @Override
        public void writeSuccess() {
            callback.writeSuccess();
        }
    }

    /**
     * Wrap future so we can increment the error count.
     */
    private class ProxyFuture<V> implements Future<V> {

        final private Future<V> future;

        private ProxyFuture(Future<V> future) {
            this.future = future;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            try {
                return future.get();
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                stats.incrementErrors();
                throw new ExecutionException(e);
            }
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return future.get(timeout, unit);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                stats.incrementErrors();
                throw new ExecutionException(e);
            }
        }
    }
}
