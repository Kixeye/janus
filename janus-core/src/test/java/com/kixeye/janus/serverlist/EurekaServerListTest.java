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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.jdom2.Element;
import org.junit.Assert;
import org.junit.Test;

import com.kixeye.janus.ServerInstance;
import com.kixeye.scout.eureka.EurekaApplication;
import com.kixeye.scout.eureka.EurekaServiceDiscoveryClient;
import com.kixeye.scout.eureka.EurekaServiceInstanceDescriptor;

public class EurekaServerListTest {
    private static final String VIP = "kvpservice";

    @Test
    public void eurekaServerListTest() {
        // get list of external service instances
    	EurekaServiceDiscoveryClient client = getMockedDiscoveryClient();
        EurekaServerList serverList = new EurekaServerList( client, VIP, false, false );
        List<ServerInstance> servers = serverList.getListOfServers();

        // assume we got at least one instance and check it has Eureka data
        EurekaServerInstance server = (EurekaServerInstance) servers.get(0);
        Assert.assertNotNull( server.getInstanceInfo() );
        Assert.assertEquals( server.getInstanceInfo().getVipAddress(), VIP );
        Assert.assertEquals( VIP, serverList.getServiceName() );
    }

    @Test
    public void eurekaServerListInternalIpTest() {
        // get list of external service instances
    	EurekaServiceDiscoveryClient client = getMockedDiscoveryClient();
        EurekaServerList serverList = new EurekaServerList( client, VIP, false, true );
        List<ServerInstance> servers = serverList.getListOfServers();

        // assume we got at least one instance and check it has Eureka data
        EurekaServerInstance server = (EurekaServerInstance) servers.get(0);
        Assert.assertNotNull( server.getInstanceInfo() );
        Assert.assertEquals( server.getInstanceInfo().getVipAddress(), VIP );
        Assert.assertEquals( VIP, serverList.getServiceName() );
    }

    private EurekaServiceDiscoveryClient getMockedDiscoveryClient() {
        Element metadata = new Element("metadata")
        		.addContent(new Element("websocketPort").setText("8180"));
        
        Element element = new Element("instance")
        		.addContent(new Element("app").setText("VIP"))
        		.addContent(new Element("vipAddress").setText(VIP))
        		.addContent(new Element("hostName").setText("localhost"))
        		.addContent(new Element("ipAddr").setText("127.0.0.1"))
        		.addContent(new Element("port").setText("80"))
        		.addContent(new Element("status").setText("UP"))
        		.addContent(metadata);

        EurekaServiceDiscoveryClient client = mock(EurekaServiceDiscoveryClient.class);
        
        try {
	        Constructor<EurekaServiceInstanceDescriptor> constructor = EurekaServiceInstanceDescriptor.class.getDeclaredConstructor(EurekaApplication.class, Element.class);
	        constructor.setAccessible(true);
	        
	        EurekaServiceInstanceDescriptor instanceInfo = constructor.newInstance(null, element);
	        when(client.describe(VIP)).thenReturn(Arrays.asList(instanceInfo));
        } catch (Exception e) {
        	throw new RuntimeException(e);
        }
        return client;
    }
}
