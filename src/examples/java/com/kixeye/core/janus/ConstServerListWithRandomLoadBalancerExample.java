package com.kixeye.core.janus;

import com.codahale.metrics.MetricRegistry;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;

/**
 * Demonstrations construction of and usage {@link Janus} with a {@link ConstServerList}
 * and {@link RandomLoadBalancer}.
 *
 *
 *
 * @author dturner@kixeye.com
 */
public class ConstServerListWithRandomLoadBalancerExample {

    public void createJanusAndSelectServer(){

        Janus<ServerStats, ServerInstance> janus =
                new Janus<ServerStats, ServerInstance>(
                        "MyServiceCluster",
                        new MetricRegistry(),
                        new ConstServerList("MyServiceCluster", "http://myserviceinstance1:8080,http://myserviceinstance2:8080"),
                        new RandomLoadBalancer(),
                        new ServerStatsFactory<ServerStats>(ServerStats.class));

        ServerStats serverStats = janus.getServer();
    }
}
