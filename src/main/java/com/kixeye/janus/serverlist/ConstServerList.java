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


import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.kixeye.janus.ServerInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a list of URLs into a server list.
 *
 * @author cbarry
 */
public class ConstServerList implements ServerList {

    private final String serviceName;
    private final List<ServerInstance> list;

    public ConstServerList(String serviceName, String... servers) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'serviceName' cannot be null or empty.");

        this.serviceName = serviceName;
        list = new ArrayList<>();
        for (String s : servers) {
            if (s == null) {
                continue;
            }
            ServerInstance server = new ServerInstance(serviceName, s.trim());
            server.setAvailable(true);
            list.add(server);
        }
    }

    /**
     * gets the service cluster name
     *
     * @return the service cluster name
     */
    @Override
    public String getServiceName() {
        return serviceName;
    }

    /**
     * gets the {@link List} list of server instances
     *
     * @return list of server instances
     */
    @Override
    public List<ServerInstance> getListOfServers() {
        return list;
    }
}
