package com.kixeye.janus.client.websocket;

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