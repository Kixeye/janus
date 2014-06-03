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


import java.nio.ByteBuffer;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.SocketUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.SettableFuture;
import com.kixeye.core.janus.Janus;
import com.kixeye.core.janus.ServerInstance;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.ServerStatsFactory;
import com.kixeye.core.janus.client.TestRestService;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.transport.dto.Envelope;
import com.kixeye.core.transport.serde.MessageSerDe;
import com.kixeye.core.transport.serde.converter.JsonMessageSerDe;
import com.kixeye.core.transport.websocket.WebSocketMessageRegistry;
import com.netflix.config.ConfigurationManager;

public class StatelessWebSocketClientTest {

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

    private void runPingTest(AnnotationConfigWebApplicationContext context, String url) throws Exception {
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
        SettableFuture<Void> future = SettableFuture.create();
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setTrustAll(true);
        StatelessWebSocketClient<ServerStats,ServerInstance> client = new StatelessWebSocketClient<ServerStats,ServerInstance>(janus,0,"/json", new WebSocketClient(sslContextFactory), new PongListener(future) );
        // send evelope w/ null message
        Envelope envelope = new Envelope("ping",null,null,null);

        // 1st send - start async send
        client.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)),null);

        // 2nd send - start 2nd async send and see if it triggers a Jetty exception
        client.sendBytes(ByteBuffer.wrap(serDe.serialize(envelope)),null);

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
