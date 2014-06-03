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
import com.google.common.base.Strings;
import com.kixeye.core.janus.ServerInstance;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Converts property "janus.listOfServers.{service cluster name}" into a server list. The property value
 * should be a comma-separated list of urls.
 * <p/>
 * Examples:
 * janus.listOfServers.MyServiceCluster=http://myserver1:8180
 * janus.listOfServers.MyServiceCluster=http://myserver1:8180,http://myserver2:8180
 *
 * @author cbarry@kixeye.com
 */
public class ConfigServerList implements ServerList<ServerInstance> {

    final static Logger logger = LoggerFactory.getLogger(ConfigServerList.class);

    final public static String PROPERTY_NAME_PREFIX = "janus.listOfServers";

    private final String serviceName;
    private final DynamicStringProperty propServers;
    private final AtomicReference<List<ServerInstance>> servers = new AtomicReference<List<ServerInstance>>(new ArrayList<ServerInstance>());

    /**
     * @param serviceName the service cluster name. Configuration property should end with this (janus.listOfServers.{serviceName}).
     */
    public ConfigServerList(String serviceName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'serviceName' cannot be blank.");

        this.serviceName = serviceName;

        final String propertyName = PROPERTY_NAME_PREFIX + "." + serviceName;
        propServers = DynamicPropertyFactory.getInstance().getStringProperty(propertyName, null);

        if (propServers == null) {
            logger.warn("No configuration property found with name {}. To enable load balancing using {}, add this property with your target server instances.", propertyName, ConfigServerList.class.getName());
            return;
        }

        propServers.addCallback(new Runnable() {
            @Override
            public void run() {
                convertPropertyToList();
            }
        });
        convertPropertyToList();
    }

    /**
     * gets the service cluster name
     *
     * @return service cluster name
     */
    @Override
    public String getServiceName() {
        return serviceName;
    }

    /**
     * gets the {@link List} of {@link ServerInstance}s in the service cluster
     *
     * @return the list of server instances
     */
    @Override
    public List<ServerInstance> getListOfServers() {
        return servers.get();
    }

    private void convertPropertyToList() {
        String value = propServers.get();
        if (Strings.isNullOrEmpty(value)) {
            servers.set(new ArrayList<ServerInstance>());
            return;
        }

        List<ServerInstance> newServers = new ArrayList<>();
        for (String s : value.split(",")) {
            ServerInstance server = new ServerInstance(serviceName, s.trim());
            server.setAvailable(true);
            newServers.add(server);
        }
        servers.set(newServers);
    }
}
