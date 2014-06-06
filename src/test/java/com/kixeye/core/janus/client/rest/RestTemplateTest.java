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
package com.kixeye.core.janus.client.rest;


import com.codahale.metrics.MetricRegistry;
import com.kixeye.core.janus.*;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.loadbalancer.ZoneAwareLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.janus.client.TestRestService;
import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.HashMap;
import java.util.Map;

public class RestTemplateTest {

    private final String VIP_TEST = "test";

    @Before
    public void setConfiguration() {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", 500);
    }

    @Test
    public void getNoParamsTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0, port1);
        MetricRegistry metricRegistry = new MetricRegistry();
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",metricRegistry),
                new ServerStatsFactory(ServerStats.class, metricRegistry));
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        // test assessor
        Assert.assertNotNull( client.getRestTemplate() );

        // test basic get
        String result = client.getForObject("/test_no_params", String.class);
        Assert.assertNotNull(result);
        Assert.assertEquals("pong",result);

        context.stop();
    }

    @Test
    public void getParamListTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);
        MetricRegistry metricRegistry = new MetricRegistry();
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",metricRegistry),
                new ServerStatsFactory(ServerStats.class, metricRegistry) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.getForObject("/test_params/{test}", String.class, "goofy");
        Assert.assertEquals("goofy", result);

        context.stop();
    }

    @Test
    public void getParamMapTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);

        Map<String,String> params = new HashMap();
        params.put("test","goofy");

        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",null),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.getForObject("/test_params/{test}", String.class, params);
        Assert.assertEquals("goofy", result);

        context.stop();
    }


    @Test
    public void postNoParamsTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.postForObject("/test_no_params", "post body", String.class);
        Assert.assertNotNull(result);
        Assert.assertEquals("post body",result);

        context.stop();
    }

    @Test
    public void postParamListTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.postForObject("/test_params/{test}", "body", String.class, "goofy");
        Assert.assertEquals("goofybody", result);

        context.stop();
    }

    @Test
    public void postParamMapTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);

        Map<String,String> params = new HashMap();
        params.put("test","goofy");

        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.postForObject("/test_params/{test}", "body2", String.class, params);
        Assert.assertEquals("goofybody2", result);

        context.stop();
    }

    @Test(expected = Exception.class)
    public void retryFailTest() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"https://bogus_server/"),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);
        client.getForObject("/", String.class);
    }

    @Test(expected = Exception.class)
         public void noServersTestRandomLB() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.getForObject("/", String.class);
        Assert.assertNotNull(result);
    }

    @Test(expected = Exception.class)
    public void noServersTestZoneLB() throws Exception {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST),
                new ZoneAwareLoadBalancer(VIP_TEST,"default",null),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        String result = client.getForObject("/", String.class);
        Assert.assertNotNull(result);
    }

    @Test(expected = HttpClientErrorException.class)
    public void notFoundTest() throws Exception {
        int port0 = SocketUtils.findAvailableTcpPort();
        int port1 = SocketUtils.findAvailableTcpPort();
        AnnotationConfigWebApplicationContext context = TestRestService.createContext(port0,port1);
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:" + port0),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class, new MetricRegistry()) );
        DefaultRestTemplateClient client = new DefaultRestTemplateClient(janus,0);

        try {
            client.getForObject("/not_a_real_path", String.class);
        } finally {
            context.stop();
        }
    }
}
