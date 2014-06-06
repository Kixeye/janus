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
package com.kixeye.core.janus.client.http.async;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.ListenableFuture;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.Janus;
import com.kixeye.core.janus.ServerStatsFactory;
import com.kixeye.core.janus.client.http.HttpMethod;
import com.kixeye.core.janus.client.http.HttpRequest;
import com.kixeye.core.janus.client.http.HttpResponse;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Tests the {@link AsyncHttpClient}
 *
 * @author elvir
 */
public class AsyncHttpClientTest {
    private static final String VIP_TEST = "test";

    private Server server;

    @Before
    public void initialize() throws Exception {
        server = new Server(0);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                BufferedReader reader = request.getReader();

                String line;
                while ((line = reader.readLine()) != null) {
                    response.getWriter().println(line);
                }

                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
            }
        });
        server.start();
    }

    @After
    public void destroy() throws Exception {
        server.stop();
    }

    @Test
    public void testGet() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, "http://localhost:" + server.getURI().getPort()),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        AsyncHttpClient client = new AsyncHttpClient(janus, 0);

        ListenableFuture<HttpResponse> responseFuture = client.execute(new HttpRequest(HttpMethod.GET, null, null), "/");
        HttpResponse response = responseFuture.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(response);
    }

    @Test
    public void testPost() throws Exception {
        final byte[] sentData = RandomStringUtils.randomAscii(32).getBytes(Charsets.US_ASCII);

        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST, "http://localhost:" + server.getURI().getPort()),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()));
        AsyncHttpClient client = new AsyncHttpClient(janus, 0);

        ListenableFuture<HttpResponse> responseFuture = client.execute(new HttpRequest(HttpMethod.POST, null, new ByteArrayInputStream(sentData)), "/");
        HttpResponse response = responseFuture.get(5, TimeUnit.SECONDS);
        Assert.assertNotNull(response);
        Assert.assertEquals(new String(sentData, Charsets.US_ASCII).trim(), new String(IOUtils.toByteArray(response.getBody()), Charsets.US_ASCII).trim());
    }
}
