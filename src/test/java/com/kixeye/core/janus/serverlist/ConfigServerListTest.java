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

import com.kixeye.core.janus.ServerInstance;
import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;


public class ConfigServerListTest {
    private static final String VIP = "kvpservice";

    @Test
    public void configServerListTest() throws InterruptedException {
        ConfigurationManager.getConfigInstance().setProperty("janus.listOfServers."+VIP, "http://localhost:8080,http://localhost:8180");

        ConfigServerList serverList = new ConfigServerList(VIP);
        List<ServerInstance> servers = serverList.getListOfServers();

        ServerInstance server = servers.get(0);
        Assert.assertEquals(server.getId(), "localhost:8080");
        Assert.assertEquals( server.getHost(), "localhost");
        Assert.assertEquals( server.getPort(), 8080);

        server = servers.get(1);
        Assert.assertEquals( server.getId(), "localhost:8180");
        Assert.assertEquals(server.getHost(), "localhost");
        Assert.assertEquals(server.getPort(), 8180);

        Assert.assertEquals( VIP, serverList.getServiceName() );
    }

    @Test
    public void cConfigServerListNoEntriesTest() throws InterruptedException {
        ConfigurationManager.getConfigInstance().setProperty("janus.listOfServers."+VIP, "");

        ConfigServerList serverList = new ConfigServerList(VIP);
        List<ServerInstance> servers = serverList.getListOfServers();
        Assert.assertTrue( servers.size() == 0);
    }
}
