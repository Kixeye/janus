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
package com.kixeye.janus;


import com.codahale.metrics.MetricRegistry;
import com.kixeye.janus.ServerInstance;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.ServerStatsFactory;

import org.junit.Assert;
import org.junit.Test;

public class BaseServerStatsFactoryTest {
    @Test
    public void normalConstructionTest() {
        ServerStatsFactory factory = new ServerStatsFactory(ServerStats.class, new MetricRegistry());
        ServerStats stats = factory.createServerStats(new ServerInstance("test","http://localhost"));
        Assert.assertNotNull(stats);

        Assert.assertEquals(0, stats.getOpenRequestCount());
        Assert.assertEquals(0, stats.getOpenSessionsCount());
        Assert.assertEquals(0, stats.getReceivedMessagesPerSecond(), 0);
        Assert.assertEquals(0, stats.getSentMessagesPerSecond(), 0);
    }

    @Test
    public void forceExceptionTest() {
        ServerStatsFactory factory = new ServerStatsFactory(BadServerStats.class,new MetricRegistry());
        ServerStats stats = factory.createServerStats(new ServerInstance("test","http://localhost"));
        Assert.assertNull(stats);
    }

    public static class BadServerStats extends ServerStats {
        private BadServerStats() {
        }
    }
}
