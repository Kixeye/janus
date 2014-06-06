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
package com.kixeye.core.janus;

import com.codahale.metrics.MetricRegistry;
//import com.kixeye.core.janus.Janus.Builder;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JanusTest {
    private final String VIP_TEST = "kvpservice";
    private static long DEFAULT_REFRESH_INTERVAL_IN_MILLIS = 500l;

    @Before
    public void setConfiguration() {
        ConfigurationManager.getConfigInstance().setProperty("janus.errorThresholdPerSec", 3);
        ConfigurationManager.getConfigInstance().setProperty("janus.shortCircuitDuration", 1000);
        ConfigurationManager.getConfigInstance().setProperty("janus.refreshIntervalInMillis", DEFAULT_REFRESH_INTERVAL_IN_MILLIS);
    }

    @Test
    public void GetServerTest() {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:0001","http://localhost:002","http://localhost:003"),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()));

        // should randomly a server from the list
        ServerStats firstResult = janus.getServer();
        boolean randomSuccess = false;
        for (int i = 0; i < 10; i++) {
            ServerStats nextResult = janus.getServer();
            if (firstResult != nextResult) {
                randomSuccess = true;
                break;
            }
        }
        Assert.assertTrue(randomSuccess);
    }

    @Test
    public void RefreshIntervalTest() {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:0001","http://localhost:002","http://localhost:003"),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()) );

        Assert.assertEquals(DEFAULT_REFRESH_INTERVAL_IN_MILLIS, janus.getRefreshInterval());

        janus.setRefreshInterval(DEFAULT_REFRESH_INTERVAL_IN_MILLIS+1);

        Assert.assertEquals(DEFAULT_REFRESH_INTERVAL_IN_MILLIS+1, janus.getRefreshInterval());
    }

    @Test
    public void ShortCircuitTest() throws InterruptedException {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST,"http://localhost:8080"),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()) );

        ServerStats stats = janus.getServer();
        Assert.assertEquals( stats.getServerInstance().isShortCircuited(), false );

        // short circuit should be 0 at this point
        Assert.assertTrue( stats.getServerInstance().getCircuitBreakerRemainingTime() <= 0.0);

        // mark 3 errors and the 3rd should cause the server instance to short circuit
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), false);
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), false);
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), true);

        // this should return null as there are no available servers
        ServerStats stats2 = janus.getServer();
        Assert.assertNull(stats2);

        // wait 1 seconds and the instance should return to normal
        Thread.sleep(1005);
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), false);

        // we should get a server again since the short circuit expired
        stats = janus.getServer();
        Assert.assertNotNull(stats);

        // mark 3 errors and the 3rd should cause the server instance to short circuit again
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), false);
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), false);
        stats.incrementErrors();
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), true);

        // are we getting a "reasonable" short circuit time out
        Assert.assertTrue( stats.getServerInstance().getCircuitBreakerRemainingTime() > 0.0);

        // this is a second short circuit in under the duration so timeout is longer
        Thread.sleep(1005);
        Assert.assertEquals(stats.getServerInstance().isShortCircuited(), true);
    }

    @Test
    public void noServersTest() {
        Janus janus = new Janus(
                VIP_TEST,
                new ConstServerList(VIP_TEST),
                new RandomLoadBalancer(),
                new ServerStatsFactory(ServerStats.class,new MetricRegistry()) );
        Assert.assertNull(janus.getServer());
    }

    @Test
    public void defaultBuilder() {
        Janus janus = Janus.builder(VIP_TEST).build();
        Assert.assertNull(janus.getServer());
    }

    @Test
    public void builderWithServers(){
        Janus janus = Janus.builder(VIP_TEST).withServers("http://localhost:8080").build();
        Assert.assertEquals("localhost", janus.getServer().getServerInstance().getHost());
    }

    @Test
    public void builderWithConstServerList(){
        Janus janus = Janus.builder(VIP_TEST).withServerList(new ConstServerList(VIP_TEST, "http://localhost:8080")).build();
        Assert.assertEquals("localhost", janus.getServer().getServerInstance().getHost());
    }

    @Test
    public void builderWithRefreshInterval(){
        Janus janus = Janus.builder(VIP_TEST).withRefreshIntervalInMillis(100).build();
        Assert.assertEquals(100, janus.getRefreshInterval());
    }
//
//    @Test
//    public void defaultBuilder_withConstServerList() {
//        Builder builder = Janus.builder(VIP_TEST);
//        builder.withServerList(new ConstServerList(VIP_TEST, "http://localhost:0001"));
//        Janus<ServerStats, ServerInstance> janus = builder.build();
//        Assert.assertEquals("localhost", janus.getServer().getServerInstance().getHost());
//    }
//
//    @Test
//    public void defaultBuilder_forServers() {
//        Builder builder = Janus.builder(VIP_TEST);
//        builder.withServers("http://localhost:0001");
//        Janus<ServerStats, ServerInstance> janus = builder.build();
//        Assert.assertEquals("localhost", janus.getServer().getServerInstance().getHost());
//    }
}
