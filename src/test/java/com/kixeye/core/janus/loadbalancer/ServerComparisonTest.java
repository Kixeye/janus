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
package com.kixeye.core.janus.loadbalancer;


import org.junit.Assert;
import org.junit.Test;

public class ServerComparisonTest {
    static final double escapeAreaThreshold = 0.9;
    static final double escapeRegionThreshold = 0.5;
    static final double escapeAvailabilityThreshold = 0.2;

    @Test
    public void locationTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 3;
        b.load = 0;
        b.sessionCount = 0;

        Assert.assertTrue(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertFalse(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void escapeAZTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0.3;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 3;
        b.load = 0;
        b.sessionCount = 0;

        Assert.assertFalse(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertTrue(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void escapeRegionTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0.6;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 1;
        b.load = 0;
        b.sessionCount = 0;

        Assert.assertFalse(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertTrue(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void escapeAreaTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0.95;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 1;
        b.load = 0;
        b.sessionCount = 0;

        Assert.assertFalse(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertTrue(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void loadTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0.0;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 7;
        b.load = 0.5;
        b.sessionCount = 0;

        Assert.assertTrue(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertFalse(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void sessionTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0;
        a.sessionCount = 0;

        ZoneAwareLoadBalancer.MetaData b = new ZoneAwareLoadBalancer.MetaData();
        b.locationBits = 7;
        b.load = 0;
        b.sessionCount = 20;

        Assert.assertTrue(a.isBetterThan(b, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
        Assert.assertFalse(b.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold));
    }

    @Test
    public void randomTest() {
        ZoneAwareLoadBalancer.MetaData a = new ZoneAwareLoadBalancer.MetaData();
        a.locationBits = 7;
        a.load = 0;
        a.sessionCount = 0;

        // X runs should produce at least one different result
        boolean success = false;
        boolean firstResult = a.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold);
        for (int i = 0; i < 10; i++) {
            if (a.isBetterThan(a, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold) != firstResult) {
                success = true;
                break;
            }
        }
        Assert.assertTrue( success );
    }
}
