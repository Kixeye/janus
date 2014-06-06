package com.kixeye.core.janus;

import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.serverlist.ConstServerList;

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

//    public void createJanusAndSelectServer() {
//        //used to track metrics about service instance usage. this MetricRegistry can shared with other parts of your softward
//        MetricRegistry metricRegistry = new MetricRegistry();
//
//        //create a ConstServerList, with a static array of urls for each service instance
//        ServerList<ServerInstance> serverList = new ConstServerList("UserService", "http://userserviceinstance1:8080,http://userserviceinstance2:8080");
//
//        //create a RandomLoadBalancer, which will randomly select only of the service instances configured in the ConstServerList
//        LoadBalancer<ServerStats<ServerInstance>> loadBalancer = new RandomLoadBalancer();
//
//        //create ServerStatsFactory, which will be responsible for creating instances of the appropriate ServerStats for each ServerInstance
//        ServerStatsFactory<ServerStats<ServerInstance>, ServerInstance> serverStatsFactory = new ServerStatsFactory<ServerStats<ServerInstance>, ServerInstance>(ServerStats.class, metricRegistry);
//
//        //put everything together to create your Janus instance
//        Janus<ServerStats<ServerInstance>, ServerInstance> janus =
//                new Janus<ServerStats<ServerInstance>, ServerInstance>(
//                        serverList.getServiceName(),
//                        serverList,
//                        loadBalancer,
//                        serverStatsFactory);
//
//        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
//        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
//        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
//        ServerStats server = janus.getServer();
//    }
//
//    public void createJanusAndSelectServerWithServers() {
//        //create a builder
//        DefaultBuilder builder = Janus.defaultBuilder("UserService");
//
//        //provide a static list of urls fo the server instances that should be used.
//        builder.withServers("http://userserviceinstance1:8080,http://userserviceinstance2:8080");
//        builder.withRandomLoadBalancing();
//
//        //build the janis instance that will manage
//        Janus janus = builder.build();
//
//        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
//        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
//        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
//        ServerStats server = janus.getServer();
//    }
//
//    public void createJanusAndSelectServerWithServerList() {
//        ServerList<ServerInstance> serverList = new ConstServerList("UserService", "http://userserviceinstance1:8080,http://userserviceinstance2:8080");
//
//        //create a builder
//        DefaultBuilder builder = Janus.defaultBuilder("UserService");
//
//        //provide a static list of urls fo the server instances that should be used.
//        builder.withServerList(serverList);
//
//        //build the janis instance that will manage
//        Janus janus = builder.build();
//
//        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
//        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
//        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
//        ServerStats server = janus.getServer();
//    }
//
//    public void eureka() {
//        //create a builder
//        EurekaBuilder builder = Janus.eurekaBuilder("UserService");
//
//        builder.withEurekaUrl("http://myeureka1", "http://myeureka1");
//        builder.withLoadBalancer(new RandomLoadBalancer());
//
//        //build the janis instance that will manage
//        Janus janus = builder.build();
//
//        //ask Janus for a server. Janus will ask the ServerList for all the service instances. It will then
//        //take the returned list of ServerInstances and given them to its LoadBalancer. The LoadBalancer will pick
//        //the "best" server instance and return it back the Janus.  Janus will then give that server to you.
//        ServerStats server = janus.getServer();
//    }
}
