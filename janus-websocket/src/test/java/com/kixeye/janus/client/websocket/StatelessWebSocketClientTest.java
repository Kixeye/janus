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
import com.kixeye.chassis.transport.serde.converter.JsonMessageSerDe;
import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.ServerStatsFactory;
import com.kixeye.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.janus.serverlist.ConstServerList;
import com.netflix.config.ConfigurationManager;

public class StatelessWebSocketClientTest {

    private final String VIP_TEST = "test";

    private MessageSerDe serDe;
    
    private int serverPort;
    private Server server;
    
    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", 500);

        serDe = new JsonMessageSerDe();
        
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
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, url),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        SettableFuture<Void> future = SettableFuture.create();
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true);
        StatelessWebSocketClient client = new StatelessWebSocketClient(janus, 0, "/json", new WebSocketClient(sslContextFactory), new PongListener(future));
        // send evelope w/ null message
        Envelope envelope = new Envelope("ping", null, null, null);

        // 1st send - start async send
        client.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)), null);

        // 2nd send - start 2nd async send and see if it triggers a Jetty exception
        client.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)), null);

        // 3rd send - start blocking send and see this triggers a Jetty exception
        client.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));

        // wait for a response
        future.get();
        client.close();
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
                Assert.fail("Exception processing web socket message");
            }
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            Assert.fail("onWebSocketConnect should not be called");
        }

        @Override
        public void onWebSocketConnect(Session session) {
            Assert.fail("onWebSocketConnect should not be called");
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
