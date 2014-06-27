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
package com.kixeye.janus.client.websocket;

import org.eclipse.jetty.websocket.api.WriteCallback;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * Represents a channel of communication with a endpoint using the
 * websocket protocol.
 *
 * @author cbarry@kixeye.com
 */
public interface WebSocketSession {

    /**
     * Close the session
     */
    void close();

    /**
     * Whether or not the session has is open
     * @return boolean
     */
    boolean isOpen();

    /**
     * Write the given bytes to the remote endpoint, blocking until all the
     * bytes have been transmitted.
     *
     * @param data the bytes to send
     * @throws IOException
     */
    void sendBytes(ByteBuffer data) throws IOException;

    /**
     * Write the given bytes to the remote endpoint. This method is asynchronous
     * and the status of the write can be checked by the client by inspecting the
     * returned {@link Future}
     *
     * @param data the bytes to send
     * @return
     */
    Future<Void> sendBytesByFuture(ByteBuffer data);

    /**
     * Write the given bytes to the remote endpoint asynchronously. Clients can
     * provide a callback to be notified when the bytes have been transmitted (or failed to transmit).
     *
     * @param data the bytes to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     */
    void sendBytes(ByteBuffer data, WriteCallback callback);

    /**
     * Write the text to the remote endpoint, blocking until all the
     * data has been transmitted.
     *
     * @param text the text to send
     * @throws IOException
     */
    void sendString(String text) throws IOException;

    /**
     * Write the given text to the remote endpoint. This method is asynchronous
     * and the status of the write can be checked by the client by inspecting the
     * returned {@link Future}.
     *
     * @param text the text to send
     * @return
     */
    Future<Void> sendStringByFuture(String text);

    /**
     * Write the given text to the remote endpoint asynchronously. Clients can
     * provide a callback to be notified when the text has been transmitted (or failed to transmit).
     *
     * @param text the text to send
     * @param callback the callback that will be notified when the bytes have been transmitted (or failed to transmit).
     */
    void sendString(String text, WriteCallback callback);
}
