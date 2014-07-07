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


import org.junit.Assert;
import org.junit.Test;

import com.kixeye.janus.loadbalancer.ZoneAwareLoadBalancer;

public class LocationParsingTest {
    @Test
    public void normalTest() {
        ZoneAwareLoadBalancer.Location location = new ZoneAwareLoadBalancer.Location("us-west-1a");
        Assert.assertEquals("us-west-1a", location.getAvailabilityZone());
        Assert.assertEquals("us-west-1", location.getRegion());
        Assert.assertEquals("us", location.getArea());
    }

    @Test
    public void reversedAZTest() {
        ZoneAwareLoadBalancer.Location location = new ZoneAwareLoadBalancer.Location("us-west-a1");
        Assert.assertEquals("us-west-a1", location.getAvailabilityZone());
        Assert.assertEquals("us-west-1", location.getRegion());
        Assert.assertEquals("us", location.getArea());
    }

    @Test
    public void malformedTest() {
        ZoneAwareLoadBalancer.Location location = new ZoneAwareLoadBalancer.Location("xxx");
        Assert.assertEquals("xxx", location.getAvailabilityZone());
        Assert.assertEquals("default", location.getRegion());
        Assert.assertEquals("default", location.getArea());
    }

    @Test
    public void defaultTest() {
        ZoneAwareLoadBalancer.Location location = new ZoneAwareLoadBalancer.Location("default");
        Assert.assertEquals("default", location.getAvailabilityZone());
        Assert.assertEquals("default", location.getRegion());
        Assert.assertEquals("default", location.getArea());
    }

    @Test
    public void unknownTest() {
        ZoneAwareLoadBalancer.Location location = new ZoneAwareLoadBalancer.Location("UNKNOWN");
        Assert.assertEquals("default", location.getAvailabilityZone());
        Assert.assertEquals("default", location.getRegion());
        Assert.assertEquals("default", location.getArea());
    }

    @Test
    public void hashAndEqualsTest() {
        ZoneAwareLoadBalancer.Location a = new ZoneAwareLoadBalancer.Location("us-west-1a");
        ZoneAwareLoadBalancer.Location b = new ZoneAwareLoadBalancer.Location("us-west-1a");
        ZoneAwareLoadBalancer.Location c = new ZoneAwareLoadBalancer.Location("us-west-1c");

        // test hash code
        Assert.assertEquals(a.hashCode(),b.hashCode());
        Assert.assertNotEquals(a.hashCode(),c.hashCode());

        // test equals
        Assert.assertTrue( a.equals(b) );
        Assert.assertFalse( a.equals(c));
    }

    @Test
    public void equalsTest() {}
}
