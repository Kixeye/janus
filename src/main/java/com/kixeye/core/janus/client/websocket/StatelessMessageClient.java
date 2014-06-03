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
import com.kixeye.core.transport.dto.Envelope;
import com.kixeye.core.transport.serde.MessageSerDe;
import com.kixeye.core.transport.websocket.WebSocketEnvelope;
import com.kixeye.core.transport.websocket.WebSocketMessageRegistry;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link StatelessWebSocketClient} which serializes message objects before transmission and
 * de-serializes message objects upon receipt.
 *
 * This class uses a defined enveloping message protocol (@see {@link Envelope}) to wrap the given
 * message objects. Within the {@link Envelope} is a typeId, which is used by both client and server
 * to determine the appropriate Java type to serialize to and de-serialize from.
 *
 * When creating an object of this class, you must provide a {@link MessageSerDe}, which is responsible for
 * performing the serialization and de-serialization of your objects.
 *
 * Additionally, you must provide a {@link WebSocketMessageRegistry} object, which maps your typeIds to message {@link Class}.
 *
 * @author cbarry@kixeye.com
 */
public class StatelessMessageClient<X extends ServerStats, Y extends ServerInstance> extends StatelessWebSocketClient<X, Y> {

    private final static Logger logger = LoggerFactory.getLogger(StatelessMessageClient.class);

    protected final MessageSerDe serDe;
    protected final WebSocketMessageRegistry messageRegistry;
    protected final MessageListener listener;

    /**
     *
     * @param janus the {@link Janus} instance that will select the appropriate remote server instance.
     * @param numRetries maximum number of send retries
     * @param webSocketClient the web socket client to use
     * @param listener listener to be informed when new messages arrive
     * @param serDe the {@link MessageSerDe} to serialize/de-serialize your message objects with
     * @param messageRegistry the {@link WebSocketMessageRegistry} used to register your message types with a typeId
     */
    public StatelessMessageClient(Janus<X, Y> janus, int numRetries, WebSocketClient webSocketClient, MessageListener listener, MessageSerDe serDe, WebSocketMessageRegistry messageRegistry) {
        super(janus, numRetries, "/" + serDe.getMessageFormatName(), webSocketClient, new WebSocketToMessageListenerBridge(listener, serDe, messageRegistry));
        this.serDe = Preconditions.checkNotNull(serDe, "'serDe' cannot be null.");
        this.messageRegistry = Preconditions.checkNotNull(messageRegistry,"'messageRegistry' cannot be null.");
        this.listener = Preconditions.checkNotNull(listener,"'listener' cannot be null");
    }

    /**
     * Blocking send that waits until the write completes before returning.
     *
     * @param message the message that will be serialize and sent as the {@link Envelope payload}
     * @param action the action that should be performed upon receipt of the message
     * @param transId opaque transaction ID
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     * @throws IOException
     */
    public void sendMessage(Object message, String action, String transId) throws NoServerAvailableException, RetriesExceededException, IOException {
        Envelope envelope = new Envelope();
        envelope.action = action;
        envelope.transactionId = transId;
        envelope.typeId = messageRegistry.getTypeIdByClass(message.getClass());
        envelope.payload = ByteBuffer.wrap(serDe.serialize(message));
        sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));
    }

    /**
     * Asynchronous send that returns immediately and calls the write callback when complete.  If
     * you do not care about complete notification, you can pass in "null" for the callback.
     *
     * @param message the message that will be serialize and sent as the {@link Envelope payload}
     * @param action the action that should be performed upon receipt of the message
     * @param transId opaque transaction ID
     * @param callback that callback that will be notified about message events
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     * @throws IOException
     */
    public void sendMessage(Object message, String action, String transId, final WriteCallback callback) throws NoServerAvailableException, RetriesExceededException, IOException {
        Envelope envelope = new Envelope();
        envelope.action = action;
        envelope.transactionId = transId;
        envelope.typeId = messageRegistry.getTypeIdByClass(message.getClass());
        envelope.payload = ByteBuffer.wrap(serDe.serialize(message));
        sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)), callback);
    }

    protected static class WebSocketToMessageListenerBridge implements WebSocketListener {

        protected final MessageListener listener;
        protected final MessageSerDe serDe;
        protected final WebSocketMessageRegistry messageRegistry;

        public WebSocketToMessageListenerBridge(MessageListener listener, MessageSerDe serDe, WebSocketMessageRegistry messageRegistry) {
            this.listener = listener;
            this.serDe = serDe;
            this.messageRegistry = messageRegistry;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            try {
                final Envelope envelope = serDe.deserialize(payload, offset, len, Envelope.class);

                // check if we have a payload
                if (StringUtils.isBlank(envelope.typeId)) {
                    if (envelope.payload != null) {
                        logger.error("Missing typeId in WebSocket envelope, ignoring message!");
                    } else {
                        // empty payload so just send the envelop to listener
                        listener.onMessage(new WebSocketEnvelope(envelope), null);
                    }
                    return;
                }

                // de-serialize message
                Class<?> clazz = messageRegistry.getClassByTypeId(envelope.typeId);
                final byte[] rawPayload = envelope.payload.array();
                final Object message = serDe.deserialize(rawPayload, 0, rawPayload.length, clazz);

                // notify the listener
                listener.onMessage(new WebSocketEnvelope(envelope), message);

            } catch (Exception e) {
                logger.error("Exception handling web socket message", e);
            }
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
        }

        @Override
        public void onWebSocketConnect(Session session) {
        }

        @Override
        public void onWebSocketError(Throwable cause) {
        }

        @Override
        public void onWebSocketText(String message) {
        }
    }

}
