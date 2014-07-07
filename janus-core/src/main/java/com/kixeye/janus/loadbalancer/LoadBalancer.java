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

import java.util.Collection;

import com.kixeye.janus.ServerStats;

/**
 * Strategy interface for the selection of server instances to connect to
 * or send messages to.  Implementations should use the given collection of
 * {@link com.kixeye.janus.ServerStats} to determine the appropriate server to return.
 *
 * @author cbarry@kixeye.com
 */
public interface LoadBalancer {

    /***
     * Choose an available server from the list of servers.  The list may
     * contain unavailable servers, i.e. those currently short circuited,
     * so the load balancer implementation is responsible for filtering
     * those out.
     *
     * @return server if available, null otherwise
     */
   ServerStats choose(Collection<ServerStats> serverStats);
}
