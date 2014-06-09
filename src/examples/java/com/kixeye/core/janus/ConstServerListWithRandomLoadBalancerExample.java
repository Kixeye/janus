package com.kixeye.core.janus;

import com.codahale.metrics.MetricRegistry;
import com.kixeye.core.janus.Janus.Builder;
import com.kixeye.core.janus.loadbalancer.LoadBalancer;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.janus.serverlist.ServerList;

//import com.kixeye.core.janus.Janus.Builder;

/**
 * Demonstrations construction of and usage {@link Janus} with a {@link ConstServerList}
 * and {@link RandomLoadBalancer}.
 * <p/>
 * In this example, the client talks to 2 downstream service clusters: UserService, ScoreService. Notice
 * that we'll create 2 {@link Janus} instances, 1 for each cluster.
 * <p/>
 * Here we're configuring {@link Janus} with the {@link ConstServerList}, which simply takes an array
 * of urls for each service instance.
 * <p/>
 * We're also using the {@link RandomLoadBalancer}, which will randomly select one of the server's configured in the
 * {@link ConstServerList}.
 *
 * @author dturner@kixeye.com
 */
public class ConstServerListWithRandomLoadBalancerExample {

    public void createJanusManually() {
        //used to track metrics about service instance usage. this MetricRegistry can shared with other parts of your softward
        MetricRegistry metricRegistry = new MetricRegistry();

        //create a ConstServerList, with a static array of urls for each service instance
        ServerList serverList = new ConstServerList("UserService", "http://userserviceinstance1:8080,http://userserviceinstance2:8080");

        //create a RandomLoadBalancer, which will randomly select only of the service instances configured in the ConstServerList
        LoadBalancer loadBalancer = new RandomLoadBalancer();

        //create ServerStatsFactory, which will be responsible for creating instances of the appropriate ServerStats for each ServerInstance
        ServerStatsFactory serverStatsFactory = new ServerStatsFactory(ServerStats.class, metricRegistry);

        //put everything together to create your Janus instance
        Janus janus = new Janus(serverList.getServiceName(), serverList, loadBalancer, serverStatsFactory);

        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
        ServerStats server = janus.getServer();
    }

    public void createJanusWithBuilder() {
        //create a builder
        Builder builder = Janus.builder("UserService");

        //provide a static list of urls fo the server instances that should be used.
        builder.withServers("http://userserviceinstance1:8080,http://userserviceinstance2:8080");

        //build the janis instance that will manage
        Janus janus = builder.build();

        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
        ServerStats server = janus.getServer();
    }


    public void eureka() {
        //create a builder
        Builder builder = Janus.builder("UserService");

        builder.withEureka("http://myeureka1", false, false);
        builder.withLoadBalancer(new RandomLoadBalancer());

        //build the janis instance that will manage
        Janus janus = builder.build();

        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
        ServerStats server = janus.getServer();
    }
}
