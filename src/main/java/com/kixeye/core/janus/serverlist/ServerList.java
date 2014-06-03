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

import java.util.List;

import com.kixeye.core.janus.ServerInstance;

/**
 * A strategy for discovering a list of server instances within a service cluster.
 *
 * @author cbarry@kixeye.com
 */
public interface ServerList<T extends ServerInstance> {
    /**
     * Gets the service cluster name
     * @return service cluster name
     */
    String getServiceName();

    /**
     * Gets a list of server instances within a service cluster.
     * @return
     */
    List<T> getListOfServers();
}
