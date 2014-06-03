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
package com.kixeye.core.janus.serverlist;

import com.google.common.base.Preconditions;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link ServerList} which uses a remote Eureka server to discover
 * server instances within a service cluster.
 *
 * A local cache of server instances is used and updated with period
 * polls to the Eureka server.
 *
 * Server instance discovery and cache management is actually handled by
 * Netflix's {@link DiscoveryManager}. https://github.com/Netflix/eureka/wiki
 *
 * @author cbarry@kixeye.com
 */
public class EurekaServerList implements ServerList<EurekaServerInstance> {

    private String serviceName;
    private DiscoveryClient discoveryClient;
    private boolean useSecure;
    private boolean useInternalIp;

    /**
     *
     * @param serviceName the name of the service cluster to fetch instances from
     * @param useSecure whether or not request/messages to server instances will use secure ports
     * @param useInternalIp whether or not to use internal IPs when connecting to server instances
     */
    public EurekaServerList(String serviceName, boolean useSecure, boolean useInternalIp) {
        this(DiscoveryManager.getInstance().getDiscoveryClient(), serviceName, useSecure, useInternalIp );
    }

    /**
     *
     * @param discoveryClient the {@link DiscoveryClient} to use
     * @param serviceName the name of the service cluster to fetch instances from
     * @param useSecure whether or not request/messages to server instances will use secure ports
     * @param useInternalIp whether or not to use internal IPs when connecting to server instances
     */
    public EurekaServerList(DiscoveryClient discoveryClient, String serviceName, boolean useSecure, boolean useInternalIp) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkNotNull(discoveryClient);

        this.serviceName = serviceName;
        this.useSecure = useSecure;
        this.useInternalIp = useInternalIp;
        this.discoveryClient = discoveryClient;
    }

    /**
     * getter for the service cluster
     * @return service cluster name
     */
    @Override
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Gets the current list of {@link EurekaServerInstance}s.
     * @return current server instances reported by Eureka
     */
    @Override
    public List<EurekaServerInstance> getListOfServers() {
        List<EurekaServerInstance> servers = new ArrayList<EurekaServerInstance>();
        List<InstanceInfo> instances = discoveryClient.getInstancesByVipAddress(serviceName, false, null);
        for (InstanceInfo ii : instances ) {
            String host;
            if (useInternalIp) {
                host = ii.getIPAddr();
            } else {
                host = ii.getHostName();
            }

            // get web socket ports from meta data
            int websocketPort = -1;
            int secureWebsocketPort = -1;
            Map<String, String> metaData = ii.getMetadata();
            if (metaData != null) {
                String tmp = metaData.get("websocketPort");
                if (tmp != null) {
                    websocketPort = Integer.parseInt(tmp);
                }
                tmp = metaData.get("secureWebsocketPort");
                if (tmp != null) {
                    secureWebsocketPort = Integer.parseInt(tmp);
                }
            }
            int webSocketPortToRegister = useSecure ? secureWebsocketPort : websocketPort;

            EurekaServerInstance server = new EurekaServerInstance(serviceName, ii.getId(), host, useSecure, ii.getPort(), webSocketPortToRegister, ii);
            server.setAvailable(InstanceInfo.InstanceStatus.UP == ii.getStatus());
            servers.add(server);
        }
        return servers;
    }
}
