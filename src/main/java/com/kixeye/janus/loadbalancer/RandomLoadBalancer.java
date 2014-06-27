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
package com.kixeye.janus.loadbalancer;

import com.kixeye.janus.ServerStats;

import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Randomly chooses a server from the given collection of servers.
 *
 * @author cbarry@kixeye.com
 */
public class RandomLoadBalancer implements LoadBalancer {

    /**
     * @param serverStats the collection of {@link com.kixeye.janus.ServerStats} to choose from
     * @return the chosen {@link com.kixeye.janus.ServerStats}
     * @see {@link LoadBalancer#choose(java.util.Collection)}
     */
    @Override
    public ServerStats choose(Collection<ServerStats> serverStats) {
        // early out if no servers
        if (serverStats.isEmpty()) {
            return null;
        }

        // filter down list to available servers
        List<ServerStats> availableServers = new ArrayList<>(serverStats.size());
        for (ServerStats s : serverStats) {
            if (s.getServerInstance().isAvailable()) {
                availableServers.add(s);
            }
        }

        // done if no available servers
        if (availableServers.isEmpty()) {
            return null;
        }

        // randomly pick one of the available servers

        int index = RandomUtils.nextInt(0, availableServers.size());
        return availableServers.get(index);
    }

}
