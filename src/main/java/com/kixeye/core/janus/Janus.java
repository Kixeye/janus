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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.kixeye.core.janus.loadbalancer.LoadBalancer;
import com.kixeye.core.janus.loadbalancer.RandomLoadBalancer;
import com.kixeye.core.janus.loadbalancer.SessionLoadBalancer;
import com.kixeye.core.janus.loadbalancer.ZoneAwareLoadBalancer;
import com.kixeye.core.janus.serverlist.ConfigServerList;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.janus.serverlist.EurekaServerList;
import com.kixeye.core.janus.serverlist.ServerList;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides the main entry point for retrieving the "best" server instance within a single service cluster.
 * It coordinates the discovery of available server instances (via {@link ServerList})
 * to produce the best candidate server instance.with a load balancing strategy {@link LoadBalancer}
 * <p/>
 * {@link Janus} will cache internally the server instances returned by the {@link ServerList} for a period of time, and will only ask
 * the {@link ServerList} for server instances when the period has expired.  By default, the service instances will be cached for 30 seconds,
 * and this can be overridden by calling setRefreshIntervalInMillis OR by setting/updating the property "janus.refreshIntervalInMillis".
 * <p/>
 * {@link Janus} delegates the responsibility of service instance discovery to the {@link ServerList} provided to it, allowing for configurable
 * discovery strategies.  Janus provides some strategies out of the box:
 *
 * @author cbarry@kixeye.com
 * @see {@link com.kixeye.core.janus.serverlist.ConfigServerList}
 * @see {@link com.kixeye.core.janus.serverlist.ConstServerList}
 * @see {@link com.kixeye.core.janus.serverlist.EurekaServerList}
 *      <p/>
 *      {@link Janus} also delegates the responsibility of service instance selection to the {@link LoadBalancer} provided to it, allowing for configurable
 *      load balancing strategies.  Janus provides some strategies out of the box:
 * @see {@link com.kixeye.core.janus.loadbalancer.RandomLoadBalancer}
 * @see {@link com.kixeye.core.janus.loadbalancer.SessionLoadBalancer}
 * @see {@link com.kixeye.core.janus.loadbalancer.ZoneAwareLoadBalancer}
 *      <p/>
 *      For clients using HTTP or WebSockets to talk to their services, the Janus library provides some clients which integrate {@link Janus} for invoking
 *      remote service endpoints.
 * @see {@link com.kixeye.core.janus.client.rest.DefaultRestTemplateClient}
 * @see {@link com.kixeye.core.janus.client.http.async.AsyncHttpClient}
 * @see {@link com.kixeye.core.janus.client.websocket.SessionWebSocketClient}
 * @see {@link com.kixeye.core.janus.client.websocket.StatelessWebSocketClient}
 * @see {@link com.kixeye.core.janus.client.websocket.StatelessMessageClient}
 */
public class Janus {

    private static final Logger logger = LoggerFactory.getLogger(Janus.class);
    public static final String REFRESH_INTERVAL_IN_MILLIS = "janus.refreshIntervalInMillis";
    public static final long DEFAULT_REFRESH_INTERVAL_IN_MILLIS = 30000;

    private final String serviceName;
    private final ServerList serverList;
    private final LoadBalancer loadBalancer;
    private final StatsFactory statsFactory;
    private final DynamicLongProperty refreshInterval = DynamicPropertyFactory.getInstance().getLongProperty(REFRESH_INTERVAL_IN_MILLIS, DEFAULT_REFRESH_INTERVAL_IN_MILLIS);

    // cache of server lists
    private final Map<String, ServerStats> servers = new ConcurrentHashMap<>();
    private final AtomicBoolean updatingServer = new AtomicBoolean(false);
    private long nextUpdateTime = -1;

    /**
     * @param serviceName  the name of the service cluster
     * @param serverList   the {@link ServerList} implementation
     * @param loadBalancer the {@link LoadBalancer} implementation
     * @param statsFactory factory class for the creation of {@link ServerStats}
     */
    public Janus(String serviceName, ServerList serverList, LoadBalancer loadBalancer, StatsFactory statsFactory) {
        this.serviceName = serviceName;
        this.serverList = serverList;
        this.loadBalancer = loadBalancer;
        this.statsFactory = statsFactory;
        updateServerList();
    }

    /**
     * @param serviceName  the name of the service cluster
     * @param serverList   the {@link ServerList} implementation
     * @param loadBalancer the {@link LoadBalancer} implementation
     * @param statsFactory factory class for the creation of {@link ServerStats}
     * @param refreshInterval the refresh interval (in millis) to refresh Janus's cache of servers.
     */
    public Janus(String serviceName, ServerList serverList, LoadBalancer loadBalancer, StatsFactory statsFactory, long refreshInterval) {
        this.serviceName = serviceName;
        this.serverList = serverList;
        this.loadBalancer = loadBalancer;
        this.statsFactory = statsFactory;
        setRefreshInterval(refreshInterval);
        updateServerList();
    }

    /**
     * Sets the refresh interval of the internal server instance cache.
     *
     * @param refreshInterval the refreshInterval to set
     */
    public void setRefreshInterval(long refreshInterval) {
        ConfigurationManager.getConfigInstance().setProperty(REFRESH_INTERVAL_IN_MILLIS, refreshInterval);
    }

    /**
     * Getter for refreshInteval
     *
     * @return the current refreshInterval
     */
    public long getRefreshInterval() {
        return refreshInterval.get();
    }

    /**
     * Get the service cluster name associated with janus instance
     *
     * @return service name
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Get a single server instance chosen through the {@link LoadBalancer}
     *
     * @return a server instance chosen through the load balancer.
     */
    public ServerStats getServer() {
        updateServerList();
        //TODO filter out unavailable servers before sending to loadBalancer?
        return loadBalancer.choose(servers.values());
    }

    private void updateServerList() {
        // only allow one thread to update the server list
        if (!updatingServer.compareAndSet(false, true)) {
            return;
        }

        try {
            // has the update interval been met?
            long now = System.currentTimeMillis();
            if (nextUpdateTime > now) {
                return;
            } else {
                nextUpdateTime = now + refreshInterval.get();
            }

            // update server stats with current availability
            for (ServerInstance s : serverList.getListOfServers()) {
                ServerStats stat = servers.get(s.getId());
                if (stat != null) {
                    stat.getServerInstance().setAvailable(s.isAvailable());
                } else {
                    stat = statsFactory.createServerStats(s);
                    servers.put(s.getId(), stat);
                }
            }

            // tick all the servers and remove from list if requested
            Iterator<Map.Entry<String, ServerStats>> iter = servers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, ServerStats> entry = iter.next();
                ServerInstance s = entry.getValue().getServerInstance();
                if (!s.tick()) {
                    iter.remove();
                }
            }
        } catch (Exception e) {
            logger.error("Exception updating the server list", e);
        } finally {
            updatingServer.set(false);
        }
    }

    /**
     * Create an instance of {@link Builder}
     *
     * @param serviceName the service cluster name that the {@link Janus} will use
     * @return the builder
     */
    public static Builder builder(String serviceName) {
        return new Builder(serviceName);
    }

    /**
     * builder for create {@link Janus} instances.  Provides
     * defaults for components needed by {@link Janus} that can
     * be overridden.  Also provides convenience methods for configuring
     * @{link Janus}.
     */
    public static class Builder {

        private String serviceName;
        private ServerList serverList;
        private LoadBalancer loadBalancer;
        private StatsFactory statsFactory;
        private MetricRegistry metricRegistry = new MetricRegistry();
        private Long refreshIntervalInMillis = Janus.DEFAULT_REFRESH_INTERVAL_IN_MILLIS;

        public Builder(String serviceName){
            Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'serviceName' cannot be null or empty.");
            this.serviceName = serviceName;
        }

        /**
         * constructs {@link Janus} with a {@link EurekaServerList}
         * @param eurekaServiceUrl the url Eureka is listening on
         * @param useSecure whether or not to communicate with discovered services in a secure manor.
         * @param useInternalIp whether or not to communicate with discovered services using a private IP address.
         * @return the Builder
         */
        public Builder withEureka(String eurekaServiceUrl, boolean useSecure, boolean useInternalIp) {
            ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
            ConfigurationManager.getConfigInstance().setProperty("eureka.region", "default");
            ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default", eurekaServiceUrl);

            DiscoveryManager discoveryManager = DiscoveryManager.getInstance();
            discoveryManager.initComponent(new MyDataCenterInstanceConfig(), new DefaultEurekaClientConfig());

            this.serverList = new EurekaServerList(serviceName, useSecure, useInternalIp);
            return this;
        }

        /**
         * constructs {@link Janus} with a pre-configured {@link EurekaServerList}
         * @param serverList the pre-configured EurekaServerList to user for service discovery
         * @return the builder
         */
        public Builder withEureka( EurekaServerList serverList) {
            this.serverList = serverList;
            return this;
        }

        /**
         * constructs {@link Janus} using the given urls with a {@link ConstServerList}.
         * @param urls the urls of the service instances that {@link Janus} will discover.
         * @return the Builder
         */
        public Builder withServers(String...urls){
            Preconditions.checkNotNull(urls, "'urls cannot be null'");
            this.serverList = new ConstServerList(serviceName, urls);
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link RandomLoadBalancer}. This is the default {@link LoadBalancer} that
         * the Builder will use if not overridden.
         * @return the Builder
         */
        public Builder withRandomLoadBalancing(){
            this.loadBalancer = new RandomLoadBalancer();
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link SessionLoadBalancer}.
         * @return the Builder
         */
        public Builder withSessionLoadBalancing(){
            this.loadBalancer = new SessionLoadBalancer();
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link ZoneAwareLoadBalancer}.
         *
         * @param zone the zone that constructed {@link Janus} instance in running in.
         * @return the Builder
         */
        public Builder withZoneAwareLoadBalancing(String zone){
            Preconditions.checkArgument(!Strings.isNullOrEmpty(zone), "'zone' cannot be null or empty.");
            this.loadBalancer = new ZoneAwareLoadBalancer(serviceName, zone, metricRegistry);
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link ServerList}
         * @param serverList the {@link ServerList} to construct {@link Janus} with
         * @return the Builder
         */
        public Builder withServerList(ServerList serverList){
            Preconditions.checkNotNull(serverList, "'serverList cannot be null'");
            this.serverList = serverList;
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link LoadBalancer}
         * @param loadBalancer {@link LoadBalancer} to construct {@link Janus} with
         * @return the Builder
         */
        public Builder withLoadBalancer(LoadBalancer loadBalancer){
            Preconditions.checkNotNull(loadBalancer, "'loadBalancer cannot be null'");
            this.loadBalancer = loadBalancer;
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link StatsFactory}.
         * @param statsFactory the {@link StatsFactory} to construct {@link Janus} with
         * @return the Builder
         */
        public Builder withStatsFactory(StatsFactory statsFactory){
            Preconditions.checkNotNull(statsFactory, "'statsFactory cannot be null'");
            this.statsFactory = statsFactory;
            return this;
        }

        /**
         * constructs {@link Janus} with a {@link MetricRegistry}
         * @param metricRegistry the {@link MetricRegistry} to construct {@link Janus} with
         * @return the Builder
         */
        public Builder withMetricRegistry(MetricRegistry metricRegistry){
            Preconditions.checkNotNull(metricRegistry, "'metricRegistry cannot be null'");
            this.metricRegistry = metricRegistry;
            if(loadBalancer instanceof ZoneAwareLoadBalancer){
                //need to re-create the ZoneAwareLoadBalancer with the new MetricRegistry
                withZoneAwareLoadBalancing(((ZoneAwareLoadBalancer) loadBalancer).getZone());
            }
            return this;
        }

        /**
         * constructs {@link Janus} with the given refreshIntervalInMillis
         * @param refreshIntervalInMillis the interval to refresh {@link Janus}'s internal cache of server instances
         * @return the Builder
         */
        public Builder withRefreshIntervalInMillis(long refreshIntervalInMillis){
            this.refreshIntervalInMillis = refreshIntervalInMillis;
            return this;
        }

        /**
         * Builds the {@link Janus} instance
         * @return {@link Janus} instance
         */
        public Janus build(){
            setDefaults();
            return new Janus(serviceName, serverList, loadBalancer, statsFactory, refreshIntervalInMillis);
        }

        private void setDefaults() {
            if(serverList == null){
                serverList = new ConfigServerList(serviceName);
            }
            if(loadBalancer == null){
                loadBalancer = new RandomLoadBalancer();
            }
            if(statsFactory == null){
                statsFactory = new ServerStatsFactory(ServerStats.class, metricRegistry);
            }
        }
    }
}
