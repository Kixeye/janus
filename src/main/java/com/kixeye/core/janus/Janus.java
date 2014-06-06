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
import com.kixeye.core.janus.serverlist.ConfigServerList;
import com.kixeye.core.janus.serverlist.ConstServerList;
import com.kixeye.core.janus.serverlist.ServerList;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides the main entry point for retrieving the "best" server instance within a single service cluster.
 * It coordinates the discovery of available server instances (via {@link ServerList}) with a load balancing strategy {@link LoadBalancer}
 * to produce the best candidate server instance.
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
public class Janus<T extends ServerStats, U extends ServerInstance> {

    private static final Logger logger = LoggerFactory.getLogger(Janus.class);
    public static final String REFRESH_INTERVAL_IN_MILLIS = "janus.refreshIntervalInMillis";
    public static final int DEFAULT_REFRESH_INTERVAL_IN_MILLIS = 30000;

    private final String serviceName;
    private final MetricRegistry metricRegistry;
    private final ServerList<U> serverList;
    private final LoadBalancer<T> loadBalancer;
    private final ServerStatsFactory<T> statsFactory;
    private final DynamicLongProperty refreshInterval = DynamicPropertyFactory.getInstance().getLongProperty(REFRESH_INTERVAL_IN_MILLIS, DEFAULT_REFRESH_INTERVAL_IN_MILLIS);

    // cache of server lists
    private final Map<String, T> servers = new ConcurrentHashMap<String, T>();
    private final AtomicBoolean updatingServer = new AtomicBoolean(false);
    private long nextUpdateTime = -1;

    /**
     * @param serviceName    the name of the service cluster
     * @param metricRegistry metricRegistory used for server statistics tracking
     * @param serverList     the {@link ServerList} implementation
     * @param loadBalancer   the {@link LoadBalancer} implementation
     * @param statsFactory   factory class for the creation of {@link ServerStats}
     */
    public Janus(String serviceName, MetricRegistry metricRegistry, ServerList<U> serverList, LoadBalancer<T> loadBalancer, ServerStatsFactory<T> statsFactory) {
        this.serviceName = serviceName;
        this.metricRegistry = metricRegistry;
        this.serverList = serverList;
        this.statsFactory = statsFactory;
        this.loadBalancer = loadBalancer;
        updateServerList();
    }

    /**
     * Sets the refresh interval of the internal server instance cache.
     *
     * @param refreshInterval
     */
    public void setRefreshInterval(long refreshInterval) {
        ConfigurationManager.getConfigInstance().setProperty(REFRESH_INTERVAL_IN_MILLIS, refreshInterval);
    }

    /**
     * Getter for refreshInteval
     *
     * @return
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
    public T getServer() {
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
            for (U s : serverList.getListOfServers()) {
                T stat = servers.get(s.getId());
                if (stat != null) {
                    stat.getServerInstance().setAvailable(s.isAvailable());
                } else {
                    stat = statsFactory.createServerStats(metricRegistry, s);
                    servers.put(s.getId(), stat);
                }
            }

            // tick all the servers and remove from list if requested
            Iterator<Map.Entry<String, T>> iter = servers.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, T> entry = iter.next();
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

    public static Builder builder(String serviceName) {
        return new Builder(serviceName);
    }

    public static class Builder<T extends ServerStats, U extends ServerInstance> {
        private String serviceName;
        private MetricRegistry metricRegistry;
        private ServerList serverList;
        private LoadBalancer loadBalancer;
        private ServerStatsFactory statsFactory;
        private DynamicLongProperty refreshInterval = DynamicPropertyFactory.getInstance().getLongProperty(REFRESH_INTERVAL_IN_MILLIS, DEFAULT_REFRESH_INTERVAL_IN_MILLIS);

        private Builder(String serviceName){
            Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'serviceName' cannot be null or empty.");
            this.serviceName = serviceName;
        }

        public Builder withMetricRegistry(MetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;
            return this;
        }

        public Builder withServerList(ServerList serverList) {
            this.serverList = serverList;
            return this;
        }

        public Builder withLoadBalancer(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
            return this;
        }

        public Builder withStatsFactory(ServerStatsFactory statsFactory){
            this.statsFactory = statsFactory;
            return this;
        }

        public Janus<ServerStats, ServerInstance> build() {
            if (metricRegistry == null) {
                metricRegistry = new MetricRegistry();
            }
            if (serverList == null) {
                serverList = new ConfigServerList(serviceName);
            }
            if (loadBalancer == null) {
                loadBalancer = new RandomLoadBalancer();
            }
            if (statsFactory == null) {
                statsFactory = new ServerStatsFactory(ServerStats.class);
            }
            return new Janus<ServerStats, ServerInstance>(serviceName, metricRegistry, serverList, loadBalancer, statsFactory);
        }

        public Builder forServers(String... servers) {
            this.serverList = new ConstServerList(serviceName, servers);
            return this;
        }
    }
}
