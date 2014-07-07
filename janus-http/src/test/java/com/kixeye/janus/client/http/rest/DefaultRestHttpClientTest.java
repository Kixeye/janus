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
package com.kixeye.janus.client.http.rest;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
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
import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.ServerStatsFactory;
import com.kixeye.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.janus.loadbalancer.ZoneAwareLoadBalancer;
import com.kixeye.janus.serverlist.ConstServerList;
import com.kixeye.relax.HttpResponse;
import com.netflix.config.ConfigurationManager;

public class DefaultRestHttpClientTest {
	private static final Logger logger = LoggerFactory.getLogger(DefaultRestHttpClientTest.class);

    private final String VIP_TEST = "test";
    
    private Connection server1Connection = null;
	private int server1Port = -1;
	
    private Connection server2Connection = null;
	private int server2Port = -1;
	
	private Container forwardingServerContainer = new Container() {
		@Override
		public void handle(Request request, Response response) {
			testContainer.handle(request, response);
			
			try {
				response.close();
			} catch (IOException e) {
				logger.error("Unexpected exception", e);
			}
		}
	};
	
	private Container testContainer = new Container() {
		public void handle(Request request, Response response) {
			// do nada
		}
	};
	
	@After
	public void tearDown() throws Exception {
		server1Connection.close();
	}

    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", 500);

		Server server = new ContainerServer(forwardingServerContainer);
		server1Connection = new SocketConnection(server);
		
		ServerSocket socketServer = new ServerSocket(0);
		server1Port = socketServer.getLocalPort();
		socketServer.close();
		
		server1Connection.connect(new InetSocketAddress(server1Port));
		
		server = new ContainerServer(forwardingServerContainer);
		server2Connection = new SocketConnection(server);
		
		socketServer = new ServerSocket(0);
		server2Port = socketServer.getLocalPort();
		socketServer.close();
		
		server2Connection.connect(new InetSocketAddress(server2Port));
    }

    @Test
    public void getNoParamsTest() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + server1Port),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",metricRegistry),
                new ServerStatsFactory(ServerStats.class, metricRegistry));
        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");
        
        testContainer = new Container() {
			public void handle(Request req, Response resp) {
				try {
					resp.getByteChannel().write(ByteBuffer.wrap("pong".getBytes(StandardCharsets.UTF_8)));
				} catch (IOException e) {
					logger.error("Unable to write to channel.");
				}
			}
		};

        // test basic get
        String result = client.get("/test_no_params", String.class).getBody().deserialize();
        Assert.assertNotNull(result);
        Assert.assertEquals("pong",result);
    }

    @Test
    public void getParamListTest() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + server1Port),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",metricRegistry),
                new ServerStatsFactory(ServerStats.class, metricRegistry) );
        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");

        final AtomicReference<String> requestMethod = new AtomicReference<>(null);
        final AtomicReference<String> requestPath = new AtomicReference<>(null);
        
        testContainer = new Container() {
			public void handle(Request req, Response resp) {
				requestMethod.set(req.getMethod());
				requestPath.set(req.getTarget());
				
				try {
					resp.getByteChannel().write(ByteBuffer.wrap("goofy".getBytes(StandardCharsets.UTF_8)));
				} catch (IOException e) {
					logger.error("Unable to write to channel.");
				}
			}
		};

        String result = client.post("/test_params/{}", null, String.class, "goofy").getBody().deserialize();
        Assert.assertEquals("POST", requestMethod.get());
        Assert.assertEquals("/test_params/goofy", requestPath.get());
        Assert.assertEquals("goofy", result);
    }

    @Test
    public void postNoParamsTest() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + server1Port),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );

        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");

        final AtomicReference<String> requestMethod = new AtomicReference<>(null);
        final AtomicReference<String> requestPath = new AtomicReference<>(null);
        
        testContainer = new Container() {
			public void handle(Request req, Response resp) {
				requestMethod.set(req.getMethod());
				requestPath.set(req.getTarget());
				
				try {
					resp.getByteChannel().write(ByteBuffer.wrap(IOUtils.toByteArray(req.getInputStream())));
				} catch (IOException e) {
					logger.error("Unable to write to channel.");
				}
			}
		};

        String result = client.post("/test_no_params", "post body", String.class).getBody().deserialize();
        Assert.assertNotNull(result);

        Assert.assertEquals("POST", requestMethod.get());
        Assert.assertEquals("/test_no_params", requestPath.get());
    }

    @Test
    public void postParamListTest() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + server1Port),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()) );

        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");

        final AtomicReference<String> requestMethod = new AtomicReference<>(null);
        final AtomicReference<String> requestPath = new AtomicReference<>(null);
        
        testContainer = new Container() {
			public void handle(Request req, Response resp) {
				requestMethod.set(req.getMethod());
				requestPath.set(req.getTarget());
				
				try {
					resp.getByteChannel().write(ByteBuffer.wrap(IOUtils.toByteArray(req.getInputStream())));
				} catch (IOException e) {
					logger.error("Unable to write to channel.");
				}
			}
		};

        String result = client.post("/test_params/{}", "body", String.class, "goofy").getBody().deserialize();
        Assert.assertEquals("body", result);

        Assert.assertEquals("POST", requestMethod.get());
        Assert.assertEquals("/test_params/goofy", requestPath.get());
    }

    @Test(expected = Exception.class)
    public void retryFailTest() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"https://bogus_server/"),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");
        client.get("/", String.class);
    }

    @Test(expected = Exception.class)
         public void noServersTestRandomLB() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");

        String result = client.get("/", String.class).getBody().deserialize();
        Assert.assertNotNull(result);
    }

    @Test(expected = Exception.class)
    public void noServersTestZoneLB() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",null),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");

        String result = client.get("/", String.class).getBody().deserialize();
        Assert.assertNotNull(result);
    }

    @Test
    public void notFoundTest() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + server1Port),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );

        DefaultRestHttpClient client = new DefaultRestHttpClient(janus, 0, DefaultRestHttpClient.UTF8_STRING_SER_DE, "text/plain");
        
        testContainer = new Container() {
			public void handle(Request req, Response resp) {
				resp.setCode(404);
			}
		};
		
		HttpResponse<String> response = client.get("/not_a_real_path", String.class);

        Assert.assertEquals(404, response.getStatusCode());
        Assert.assertEquals("", response.getBody().deserialize());
    }
    
    public static class TestObject {
		private String testString;
		private int testInt;
		
		/**
		 * 
		 */
		public TestObject() {
		}

		/**
		 * @param testString
		 * @param testInt
		 */
		public TestObject(String testString, int testInt) {
			this.testString = testString;
			this.testInt = testInt;
		}

		/**
		 * @return the testString
		 */
		public String getTestString() {
			return testString;
		}

		/**
		 * @param testString the testString to set
		 */
		public void setTestString(String testString) {
			this.testString = testString;
		}

		/**
		 * @return the testInt
		 */
		public int getTestInt() {
			return testInt;
		}

		/**
		 * @param testInt the testInt to set
		 */
		public void setTestInt(int testInt) {
			this.testInt = testInt;
		}
	}
}
