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


import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.SettableFuture;
import com.kixeye.core.janus.*;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.janus.client.TestRestService;
import com.kixeye.core.transport.dto.Envelope;
import com.kixeye.core.transport.serde.MessageSerDe;
import com.kixeye.core.transport.serde.converter.JsonMessageSerDe;
import com.kixeye.core.transport.websocket.WebSocketMessageRegistry;
import com.netflix.config.ConfigurationManager;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.SocketUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.nio.ByteBuffer;

public class SessionWebSocketClientTest {

    private final String VIP_TEST = "test";

    private MessageSerDe serDe;
    private WebSocketMessageRegistry messageRegistry;

    @Before
    public void setConfiguration() {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", 500);
    }

    @Test
    public void pingTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0, port1);
        String url = "ws://localhost:" + port1;
        runPingTest(context,url);
    }

    @Test
    public void securePingTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        int port2 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createSecureContext(port0, port1, port2);
        String url = "wss://localhost:" + port2;
        runPingTest(context,url);
    }

    private void runPingTest(AnnotationConfigWebApplicationContext context,String url) throws Exception {
        serDe = context.getBean(JsonMessageSerDe.class);
        messageRegistry = context.getBean(WebSocketMessageRegistry.class);
        messageRegistry.registerType("ping", TestRestService.PingMessage.class);
        messageRegistry.registerType("pong", TestRestService.PongMessage.class);

        Janus<ServerStats,ServerInstance> janus = new Janus<ServerStats,ServerInstance>(
                VIP_TEST,
                new MetricRegistry(),
                new ConstServerList(VIP_TEST,url),
                new RandomLoadBalancer(),
                new ServerStatsFactory<ServerStats>(ServerStats.class) );
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true);
        SessionWebSocketClient<ServerStats,ServerInstance> client = new SessionWebSocketClient<ServerStats,ServerInstance>(janus,0,"/json", new WebSocketClient(sslContextFactory));

        // get a websocket session
        SettableFuture<Void> futureA = SettableFuture.create();
        WebSocketSession sessionA = client.getNewSession( new PongListener(futureA) );
        Assert.assertTrue( sessionA.isOpen() );

        // get another websocket session
        SettableFuture<Void> futureB = SettableFuture.create();
        WebSocketSession sessionB = client.getNewSession( new PongListener(futureB) );
        Assert.assertTrue( sessionB.isOpen() );

        // server should have two open sessions
        ServerStats stats = janus.getServer();
        Assert.assertEquals( 2, stats.getOpenSessionsCount() );

        // send ping to session A
        Envelope envelope = new Envelope("ping",null,null,null);
        sessionA.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));

        // wait for response
        futureA.get();
        Assert.assertTrue( futureA.isDone() );

        // close session A
        sessionA.close();
        Assert.assertFalse( sessionA.isOpen() );

        // server should have one open sessions
        Assert.assertEquals( 1, stats.getOpenSessionsCount() );

        // session B should still be active
        Assert.assertTrue( sessionB.isOpen() );
        Assert.assertFalse( futureB.isDone() );

        // send ping to SessionB
        sessionB.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)));

        // wait for response
        futureB.get();
        Assert.assertTrue( futureB.isDone() );

        // close session B
        sessionB.close();
        Assert.assertFalse( sessionB.isOpen() );

        // server should have zero open sessions
        Assert.assertEquals( 0, stats.getOpenSessionsCount() );

    }

    class PongListener implements WebSocketListener {

        final SettableFuture<Void> future;

        PongListener(SettableFuture<Void> future) {
            this.future = future;
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            try {
                final Envelope envelope = serDe.deserialize(payload,offset,len,Envelope.class);
                Class<?> messageClass = messageRegistry.getClassByTypeId(envelope.typeId);
                Assert.assertEquals(messageClass,TestRestService.PongMessage.class);
                byte[] rawPayload = envelope.payload.array();
                TestRestService.PongMessage pong = (TestRestService.PongMessage) serDe.deserialize(rawPayload,0,rawPayload.length,messageClass);
                Assert.assertEquals( pong.messsage, "pong" );

                // tell object we got the message
                future.set(null);
            } catch (Exception e) {
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
