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

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EurekaServerListTest {
    private static final String VIP = "kvpservice";

    @Test
    public void eurekaServerListTest() {
        // get list of external service instances
        DiscoveryClient client = getMockedDiscoveryClient();
        EurekaServerList serverList = new EurekaServerList( client, VIP, false, false );
        List<EurekaServerInstance> servers = serverList.getListOfServers();

        // assume we got at least one instance and check it has Eureka data
        EurekaServerInstance server = servers.get(0);
        Assert.assertNotNull( server.getInstanceInfo() );
        Assert.assertEquals( server.getInstanceInfo().getVIPAddress(), VIP );
        Assert.assertEquals( VIP, serverList.getServiceName() );
    }

    @Test
    public void eurekaServerListInternalIpTest() {
        // get list of external service instances
        DiscoveryClient client = getMockedDiscoveryClient();
        EurekaServerList serverList = new EurekaServerList( client, VIP, false, true );
        List<EurekaServerInstance> servers = serverList.getListOfServers();

        // assume we got at least one instance and check it has Eureka data
        EurekaServerInstance server = servers.get(0);
        Assert.assertNotNull( server.getInstanceInfo() );
        Assert.assertEquals( server.getInstanceInfo().getVIPAddress(), VIP );
        Assert.assertEquals( VIP, serverList.getServiceName() );
    }

    private DiscoveryClient getMockedDiscoveryClient() {
        Map<String,String> mockedMetaData = new HashMap<String,String>();
        mockedMetaData.put("websocketPort","8180");
        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                .setDataCenterInfo( new DataCenterInfo() {
                    @Override
                    public Name getName() {
                        return Name.MyOwn;
                    }
                })
                .setAppName(VIP)
                .setVIPAddress(VIP)
                .setHostName("localhost")
                .setIPAddr("127.0.0.1")
                .setPort(80)
                .setStatus(InstanceInfo.InstanceStatus.UP)
                .setMetadata(mockedMetaData)
                .build();
        DiscoveryClient client = mock(DiscoveryClient.class);
        when(client.getInstancesByVipAddress(VIP,false,null)).thenReturn(Arrays.asList(instanceInfo));
        return client;
    }
}
