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

import com.google.common.collect.Lists;
import com.kixeye.janus.ServerInstance;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.loadbalancer.RandomLoadBalancer;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class RandomLoadBalancerTest {
    @Test
    public void oneAvailableServerTest() {
        ServerInstance serverInstance = new ServerInstance("dummy","dummy","localhost",false,80,-1);
        serverInstance.setAvailable(true);
        ServerStats serverStats = new ServerStats();
        serverStats.setServerInstance(serverInstance);

        RandomLoadBalancer lb = new RandomLoadBalancer();
        ServerStats stat = lb.choose(Lists.newArrayList(serverStats));
        Assert.assertEquals(stat,serverStats);
        Assert.assertTrue( stat.getServerInstance().getUrl().startsWith("http://"));
    }

    @Test
    public void oneAvailableSecureServerTest() {
        ServerInstance serverInstance = new ServerInstance("dummy","dummy","localhost",true,80,-1);
        serverInstance.setAvailable(true);
        ServerStats serverStats = new ServerStats();
        serverStats.setServerInstance(serverInstance);

        RandomLoadBalancer lb = new RandomLoadBalancer();
        ServerStats stat = lb.choose(Lists.newArrayList(serverStats));
        Assert.assertEquals(stat,serverStats);
        Assert.assertTrue( stat.getServerInstance().getUrl().startsWith("https://"));
    }

    @Test
    public void oneAvailableWebSocketServerTest() {
        ServerInstance serverInstance = new ServerInstance("dummy","dummy","localhost",false,-1,81);
        serverInstance.setAvailable(true);
        ServerStats serverStats = new ServerStats();
        serverStats.setServerInstance(serverInstance);

        RandomLoadBalancer lb = new RandomLoadBalancer();
        ServerStats stat = lb.choose(Lists.newArrayList(serverStats));
        Assert.assertEquals(stat,serverStats);
    }
}
