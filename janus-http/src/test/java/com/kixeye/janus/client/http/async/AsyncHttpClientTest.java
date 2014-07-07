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
package com.kixeye.janus.client.http.async;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.ListenableFuture;
import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.ServerStatsFactory;
import com.kixeye.janus.client.http.HttpMethod;
import com.kixeye.janus.client.http.HttpRequest;
import com.kixeye.janus.client.http.HttpResponse;
import com.kixeye.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.janus.serverlist.ConstServerList;

/**
 * Tests the {@link AsyncHttpClient}
 *
 * @author elvir
 */
public class AsyncHttpClientTest {
	private static final Logger logger = LoggerFactory.getLogger(AsyncHttpClientTest.class);
	
    private static final String VIP_TEST = "test";

    private Connection connection = null;
	private int port = -1;
	private Container testContainer = new Container() {
		@Override
		public void handle(Request request, Response response) {
			try {
				response.getByteChannel().write(ByteBuffer.wrap(IOUtils.toByteArray(request.getInputStream())));
				response.close();
			} catch (IOException e) {
				logger.error("Unexpected exception", e);
			}
		}
	};
	
	@Before
	public void setUp() throws Exception {
		Server server = new ContainerServer(testContainer);
		connection = new SocketConnection(server);
		
		ServerSocket socketServer = new ServerSocket(0);
		port = socketServer.getLocalPort();
		socketServer.close();
		
		connection.connect(new InetSocketAddress(port));
	}
	
	@After
	public void tearDown() throws Exception {
		connection.close();
	}

    @Test
    public void testGet() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, "http://localhost:" + port),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        
        try (AsyncHttpClient client = new AsyncHttpClient(janus, 0)) {
	        ListenableFuture<HttpResponse> responseFuture = client.execute(new HttpRequest(HttpMethod.GET, null, null), "/");
	        HttpResponse response = responseFuture.get(5, TimeUnit.SECONDS);
	        Assert.assertNotNull(response);
        }
    }

    @Test
    public void testPost() throws Exception {
        final byte[] sentData = RandomStringUtils.randomAscii(32).getBytes(Charsets.US_ASCII);

        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, "http://localhost:" + port),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        
        try (AsyncHttpClient client = new AsyncHttpClient(janus, 0)) {
	        ListenableFuture<HttpResponse> responseFuture = client.execute(new HttpRequest(HttpMethod.POST, null, new ByteArrayInputStream(sentData)), "/");
	        HttpResponse response = responseFuture.get(5, TimeUnit.SECONDS);
	        Assert.assertNotNull(response);
	        Assert.assertEquals(new String(sentData, Charsets.US_ASCII).trim(), new String(IOUtils.toByteArray(response.getBody()), Charsets.US_ASCII).trim());
        }
    }
}
