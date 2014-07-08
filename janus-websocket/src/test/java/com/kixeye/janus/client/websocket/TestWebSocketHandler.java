package com.kixeye.janus.client.websocket;

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

import java.io.InputStream;
import java.nio.ByteBuffer;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import com.kixeye.chassis.transport.dto.Envelope;
import com.kixeye.chassis.transport.serde.MessageSerDe;

@WebSocket
public class TestWebSocketHandler {
	private MessageSerDe serDe;
	
	public TestWebSocketHandler(MessageSerDe serDe) {
		this.serDe = serDe;
	}

	@OnWebSocketMessage
	public synchronized void onWebSocketMessage(Session session, InputStream stream) throws Exception {
		Envelope incommingEnvelope = serDe.deserialize(stream, Envelope.class);
		
		if ("ping".equals(incommingEnvelope.action)) {
			Envelope outgoingEnvelope = new Envelope("ping", "pong", null, 
					ByteBuffer.wrap(serDe.serialize(new TestPongMessage("pong"))));
			session.getRemote().sendBytes(ByteBuffer.wrap(serDe.serialize(outgoingEnvelope)));
		}
	}
	
	@OnWebSocketError
	public void onWebSocketError(Session session, Throwable error) throws Exception {
		error.printStackTrace();
	}
}