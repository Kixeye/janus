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


import java.net.ServerSocket;
import java.nio.ByteBuffer;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.SettableFuture;
import com.kixeye.chassis.transport.dto.Envelope;
import com.kixeye.chassis.transport.serde.MessageSerDe;
import com.kixeye.chassis.transport.serde.converter.JsonJacksonMessageSerDe;
import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.ServerStatsFactory;
import com.kixeye.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.janus.serverlist.ConstServerList;
import com.netflix.config.ConfigurationManager;

public class SessionWebSocketClientTest {

    private final String VIP_TEST = "test";

    private MessageSerDe serDe;
    
    private int serverPort;
    private Server server;
    
    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", 500);

        serDe = new JsonJacksonMessageSerDe();
        
        ServerSocket socketServer = new ServerSocket(0);
		serverPort = socketServer.getLocalPort();
		socketServer.close();
		
		server = new Server(serverPort);
        WebSocketHandler wsHandler = new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator(new WebSocketCreator() {
					
					@Override
					public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
						return new TestWebSocketHandler(serDe);
					}
				});
            }
        };
        server.setHandler(wsHandler);
        server.start();
    }
    
    @After
    public void tearDown() throws Exception {
    	server.stop();
    }
    
    @Test
    public void pingTest() throws Exception {
        String url = "ws://localhost:" + serverPort;
        runPingTest(url);
    }

    private void runPingTest(String url) throws Exception {
//        messageRegistry = context.getBean(WebSocketMessageRegistry.class);
//        messageRegistry.registerType("ping", TestRestService.PingMessage.class);
//        messageRegistry.registerType("pong", TestRestService.PongMessage.class);

        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, url),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true);
        SessionWebSocketClient client = new SessionWebSocketClient(janus, 0, "/json", new WebSocketClient(sslContextFactory));

        // get a websocket session
        SettableFuture<Void> futureA = SettableFuture.create();
        WebSocketSession sessionA = client.getNewSession(new PongListener(futureA));
        Assert.assertTrue(sessionA.isOpen());

        // get another websocket session
        SettableFuture<Void> futureB = SettableFuture.create();
        WebSocketSession sessionB = client.getNewSession(new PongListener(futureB));
        Assert.assertTrue(sessionB.isOpen());

        // server should have two open sessions
        ServerStats stats = janus.getServer();
        Assert.assertEquals(2, stats.getOpenSessionsCount());

        // send ping to session A
        Envelope envelope = new Envelope("ping", null, null, null);
        sessionA.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));

        // wait for response
        futureA.get();
        Assert.assertTrue(futureA.isDone());

        // close session A
        sessionA.close();
        Assert.assertFalse(sessionA.isOpen());

        // server should have one open sessions
        Assert.assertEquals(1, stats.getOpenSessionsCount());

        // session B should still be active
        Assert.assertTrue(sessionB.isOpen());
        Assert.assertFalse(futureB.isDone());

        // send ping to SessionB
        sessionB.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));

        // wait for response
        futureB.get();
        Assert.assertTrue(futureB.isDone());

        // close session B
        sessionB.close();
        Assert.assertFalse(sessionB.isOpen());

        // server should have zero open sessions
        Assert.assertEquals(0, stats.getOpenSessionsCount());

    }
    
    class PongListener implements WebSocketListener {

        final SettableFuture<Void> future;

        PongListener(SettableFuture<Void> future) {
            this.future = future;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            try {
                final Envelope envelope = serDe.deserialize(payload, offset, len, Envelope.class);

                byte[] rawPayload = envelope.payload.array();
                TestPongMessage pong = serDe.deserialize(rawPayload, 0, rawPayload.length, TestPongMessage.class);
                Assert.assertEquals(pong.message, "pong");

                // tell object we got the message
                future.set(null);
            } catch (Exception e) {
            	e.printStackTrace();
                Assert.fail("Exception processing web socket message");
            }
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
        }

        @Override
        public void onWebSocketConnect(Session session) {
            Assert.assertNull(session);
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            Assert.fail("onWebSocketError should not be called");
        }

        @Override
        public void onWebSocketText(String message) {
            Assert.fail("onWebSocketText should not be called");
        }
    }
}
