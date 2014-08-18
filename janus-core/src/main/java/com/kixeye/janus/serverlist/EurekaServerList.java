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
package com.kixeye.janus.serverlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.kixeye.janus.ServerInstance;
import com.kixeye.scout.ServiceStatus;
import com.kixeye.scout.eureka.EurekaServiceDiscoveryClient;
import com.kixeye.scout.eureka.EurekaServiceInstanceDescriptor;

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
public class EurekaServerList implements ServerList {

    private String serviceName;
    private EurekaServiceDiscoveryClient discoveryClient;
    private boolean useSecure;
    private boolean useInternalIp;

    /**
     *
     * @param eurekaServiceUrl the url of the eureka service
     * @param serviceName the name of the service cluster to fetch instances from
     * @param useSecure whether or not request/messages to server instances will use secure ports
     * @param useInternalIp whether or not to use internal IPs when connecting to server instances
     */
    public EurekaServerList(String eurekaServiceUrl, String serviceName, boolean useSecure, boolean useInternalIp) {
        this(new EurekaServiceDiscoveryClient(eurekaServiceUrl, 30, TimeUnit.SECONDS), serviceName, useSecure, useInternalIp );
    }

    /**
     *
     * @param discoveryClient the {@link DiscoveryClient} to use
     * @param serviceName the name of the service cluster to fetch instances from
     * @param useSecure whether or not request/messages to server instances will use secure ports
     * @param useInternalIp whether or not to use internal IPs when connecting to server instances
     */
    public EurekaServerList(EurekaServiceDiscoveryClient discoveryClient, String serviceName, boolean useSecure, boolean useInternalIp) {
        Preconditions.checkNotNull(serviceName);
        Preconditions.checkNotNull(discoveryClient);

        this.serviceName = serviceName;
        this.useSecure = useSecure;
        this.useInternalIp = useInternalIp;
        this.discoveryClient = discoveryClient;
        
        // wait for refresh
        try {
        	long startTime = System.currentTimeMillis();
	        while (discoveryClient.getLastRefreshTime() < 0 && System.currentTimeMillis() - startTime < 5000) {
	        	Thread.sleep(100);
	        }
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }
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
    public List<ServerInstance> getListOfServers() {
    	List<EurekaServiceInstanceDescriptor> instances = discoveryClient.describe(serviceName);
        List<ServerInstance> servers = new ArrayList<ServerInstance>(instances.size());
        for (EurekaServiceInstanceDescriptor instance : instances) {
            String host;
            if (useInternalIp) {
                host = instance.getIpAddress();
            } else {
                host = instance.getHostname();
            }

            // get web socket ports from meta data
            int websocketPort = -1;
            int secureWebsocketPort = -1;
            Map<String, String> metaData = instance.getMetadata();
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

            EurekaServerInstance server = new EurekaServerInstance(serviceName, instance.getIpAddress(), host, useSecure, instance.getPort(), webSocketPortToRegister, instance);
            server.setAvailable(ServiceStatus.UP == instance.getStatus());
            servers.add(server);
        }
        return servers;
    }
}
