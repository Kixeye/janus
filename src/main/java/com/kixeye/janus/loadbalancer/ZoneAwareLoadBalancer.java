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

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.kixeye.janus.ServerInstance;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.serverlist.EurekaServerInstance;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;

import org.apache.commons.lang.math.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * This load balancer attempts to distribute traffic to servers nearest to it.
 * <p/>
 * Each server instance is given a score based on its location relative to the load balancer
 * instance (the client instance), and the server instance's current load.  The server instances score's are
 * then compared to each other and the server with the best score is selected.
 * <p/>
 * There are 3 location buckets for which a server instance will be placed: Availability zone, Region, and Area.  Preference
 * is given to server instances which are nearest to the client (the load balancing server) starting with Availability zone, then Region, and
 * finally Area.  A server instance may be "downgraded" to a lower location bucket if their load factor is considered too high relative to
 * the load factor of the other servers. Ultimately, the nearest server instance with the lowest amount of load will be selected. If multiple
 * server instances in the same location bucket are found with the same load factor, the server instance with the fewest number of active
 * sessions will be selected.
 * <p/>
 * Server instances which are unavailable (short circuited, offline etc...) will not be considered
 * <p/>
 * Thresholds for each location bucket can be configured using the following properties:
 * janus.serviceName.{service name}.escapeAvailabilityThreshold (defaults to 0.9)
 * janus.serviceName.{service name}.escapeRegionThreshold (defaults to 0.9)
 * janus.serviceName.{service name}.escapeAreaThreshold (defaults to 0.9)
 * <p/>
 * A server instance's load factor is calculated as its current (messages sent per second) / {janus.serviceName.{service cluster}.maxRequestsPerSecond} (defaults to 100)
 *
 * @author cbarry@kixeye.com
 */
public class ZoneAwareLoadBalancer implements LoadBalancer {
    private static final Logger logger = LoggerFactory.getLogger(ZoneAwareLoadBalancer.class);
    private static final String DEFAULT = "default";
    private static final String UNKNOWN = "unknown";

    private final LoadingCache<ServerStats, Location> locations;
    private final Location myLocation;
    private final DynamicDoubleProperty propMaxRequestsPerSecond;
    private final DynamicDoubleProperty propEscapeAreaThreshold;
    private final DynamicDoubleProperty propEscapeRegionThreshold;
    private final DynamicDoubleProperty propEscapeAvailabilityThreshold;
    private final MetricRegistry metricRegistry;
    private final String serviceName;
    private final ConcurrentHashMap<String, Counter> availabilityZoneToCounter = new ConcurrentHashMap<>();

    /**
     * Constructor
     *
     * @param serviceName        the service cluster name
     * @param myAvailabilityZone the availability zone of the server running the load balancer
     * @param metricRegistry     the registry for collected metrics
     */
    public ZoneAwareLoadBalancer(String serviceName, String myAvailabilityZone, MetricRegistry metricRegistry) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'serviceName' cannot be null or empty.");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(myAvailabilityZone), "'myAvailabilityZone' cannot be null or empty.");

        this.serviceName = serviceName;
        this.myLocation = new Location(myAvailabilityZone);
        this.locations = CacheBuilder.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build(new CacheLoader<ServerStats, Location>() {
                    @Override
                    public Location load(ServerStats key) throws Exception {
                        return getLocationFromServer(key);
                    }
                });

        this.propMaxRequestsPerSecond = DynamicPropertyFactory.getInstance().getDoubleProperty("janus.serviceName." + serviceName + ".maxRequestsPerSecond", 100);
        this.propEscapeAreaThreshold = DynamicPropertyFactory.getInstance().getDoubleProperty("janus.serviceName." + serviceName + ".escapeAreaThreshold", 0.9);
        this.propEscapeRegionThreshold = DynamicPropertyFactory.getInstance().getDoubleProperty("janus.serviceName." + serviceName + ".escapeRegionThreshold", 0.9);
        this.propEscapeAvailabilityThreshold = DynamicPropertyFactory.getInstance().getDoubleProperty("janus.serviceName." + serviceName + ".escapeAvailabilityThreshold", 0.9);

        this.metricRegistry = metricRegistry;
    }

    /**
     * @param serverStats the server instances to choose from
     * @return the nearest, less loaded server instance
     * @see {@link LoadBalancer#choose(java.util.Collection)}
     */
    @Override
    public ServerStats choose(Collection<ServerStats> serverStats) {
        // early out if no servers
        if (serverStats.isEmpty()) {
            return null;
        }

        // cache properties to speed up loop
        double maxRequestsPerSecond = propMaxRequestsPerSecond.get();
        double escapeAreaThreshold = propEscapeAreaThreshold.get();
        double escapeRegionThreshold = propEscapeRegionThreshold.get();
        double escapeAvailabilityThreshold = propEscapeAvailabilityThreshold.get();

        // find the best available server
        MetaData max = null;
        for (ServerStats stat : serverStats) {
            ServerStats s = (ServerStats) stat;
                    ServerInstance instance = s.getServerInstance();
            if (!instance.isAvailable()) {
                continue;
            }

            MetaData meta = new MetaData();
            meta.location = locations.getUnchecked(s);
            meta.server = s;
            meta.load = s.getSentMessagesPerSecond() / maxRequestsPerSecond;
            meta.sessionCount = s.getOpenSessionsCount();
            meta.locationBits = 0;
            if (myLocation.getAvailabilityZone().equals(meta.location.getAvailabilityZone())) {
                // same availability zone
                meta.locationBits = 7;
            } else if (myLocation.getRegion().equals(meta.location.getRegion())) {
                // same region
                meta.locationBits = 3;
            } else if (myLocation.getArea().equals(meta.location.getArea())) {
                // same area
                meta.locationBits = 1;
            }

            // keep the best server instance
            if (max == null) {
                max = meta;
            } else if (meta.isBetterThan(max, escapeAreaThreshold, escapeRegionThreshold, escapeAvailabilityThreshold)) {
                max = meta;
            }
        }

        if (max != null) {
            if (metricRegistry != null) {
                Counter counter = availabilityZoneToCounter.get(max.location.getAvailabilityZone());
                if (counter == null) {
                    counter = metricRegistry.counter(name(serviceName, "az-requests", max.location.getAvailabilityZone()));

                    Counter prevCount = availabilityZoneToCounter.putIfAbsent(max.location.getAvailabilityZone(), counter);
                    if (prevCount != null) {
                        // another thread snuck in their counter during a race condition so use it instead.
                        counter = prevCount;
                    }
                }
                counter.inc();
            }
            return max.server;
        } else {
            return null;
        }
    }

    public String getZone() {
        return myLocation.getAvailabilityZone();
    }


    /**
     * Holds load balancing meta data for a server instance.
     * (public for unit tests)
     */
    public static class MetaData {
        public ServerStats server;
        public double load;
        public Location location;
        public int locationBits;
        public long sessionCount;

        /**
         * Compare this MetaData with another and return TRUE if this is better.
         *
         * @param o Other MetaData to compare against
         * @return true if the other MetaData is a better choice
         */
        public boolean isBetterThan(MetaData o, double escapeAreaThreshold, double escapeRegionThreshold, double escapeAvailabilityThreshold) {
            double deltaLoad = load - o.load;
            double absDeltaLoad = Math.abs(deltaLoad);

            // Adjust location sensitivity if there is a major difference in load
            int locationMask = 7;
            if (absDeltaLoad > escapeAreaThreshold) {
                // allow out of area
                locationMask = 0;
            } else if (absDeltaLoad > escapeRegionThreshold) {
                // allow out of region
                locationMask = 1;
            } else if (absDeltaLoad > escapeAvailabilityThreshold) {
                // allow out of az
                locationMask = 3;
            }

            // Compare locations, higher number better
            int deltaLoc = (locationBits & locationMask) - (o.locationBits & locationMask);
            if (deltaLoc > 0) {
                return true;
            } else if (deltaLoc < 0) {
                return false;
            } else {
                // same location score so look at relative load
                if (absDeltaLoad > 0.1) {
                    if (deltaLoad < 0) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    // roughly same load so look at open sessions
                    long deltaSessions = sessionCount - o.sessionCount;
                    if (Math.abs(deltaSessions) > 10) {
                        if (deltaSessions < 0) {
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        // Roughly same number of sessions, so flip a coin.
                        // Note, this is NOT a fair distribution.  The hope is that
                        // as the last entry has a higher chance of being selected
                        // its score will drop allowing others to bubble up.
                        return RandomUtils.nextBoolean();
                    }
                }
            }
        }
    }

    private static Location getLocationFromServer(ServerStats server) {
        String availabilityZone = null;
        if (server.getServerInstance() instanceof EurekaServerInstance) {
            EurekaServerInstance instance = (EurekaServerInstance) server.getServerInstance();
            DataCenterInfo dataCenterInfo = instance.getInstanceInfo().getDataCenterInfo();
            if (dataCenterInfo instanceof AmazonInfo) {
                AmazonInfo amazonInfo = (AmazonInfo) dataCenterInfo;
                availabilityZone = amazonInfo.getMetadata().get("availability-zone");
            }
        }
        if (availabilityZone == null) {
            availabilityZone = DEFAULT;
        }
        return new Location(availabilityZone);
    }

    /**
     * Parse an AWS availability zone into AZ, Region, and area components.
     * (public to allow for unit testing)
     */
    public static class Location {
        private String availabilityZone = DEFAULT;
        private String region = DEFAULT;
        private String area = DEFAULT;

        public Location(String inAvailabilityZone) {
            inAvailabilityZone = inAvailabilityZone.toLowerCase();

            // punt if just default or unknown
            if (DEFAULT.equals(inAvailabilityZone) || UNKNOWN.equals(inAvailabilityZone)) {
                return;
            }

            // use specified availability zone
            availabilityZone = inAvailabilityZone;

            // punt on decoding if string doesn't match expected format
            String[] parts = availabilityZone.split("-");
            if (parts.length != 3) {
                logger.warn("Incorrectly formatted AZ <{}>", availabilityZone);
                return;
            }

            // extract region number
            CharMatcher ASCII_DIGITS = CharMatcher.DIGIT.precomputed();
            String regionNum = ASCII_DIGITS.retainFrom(parts[2]);
            if (Strings.isNullOrEmpty(regionNum)) {
                logger.warn("Incorrectly formatted AZ <{}>", availabilityZone);
                return;
            }

            // construct the area and region pieces
            this.area = parts[0];
            this.region = String.format("%s-%s-%d", parts[0], parts[1], Integer.parseInt(regionNum));
        }

        public String getAvailabilityZone() {
            return availabilityZone;
        }

        public String getRegion() {
            return region;
        }

        public String getArea() {
            return area;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Location location = (Location) o;

            if (!area.equals(location.area)) {
                return false;
            }
            if (!availabilityZone.equals(location.availabilityZone)) {
                return false;
            }
            if (!region.equals(location.region)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = availabilityZone.hashCode();
            result = 31 * result + region.hashCode();
            result = 31 * result + area.hashCode();
            return result;
        }
    }
}
