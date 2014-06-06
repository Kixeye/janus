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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.serverlist.EurekaServerInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Eureka specific load balancer which selects a server instance with the
 * fewest number of active sessions.
 *
 * This load balancer gets an instance's session count comes from a Eureka
 * meta-data field called 'sessions'.  Note that this is not a stock Eureka
 * feature but rather something each server instance must manually adds to
 * its heartbeat data.  Since the server instance list is cached client side,
 * the load balancer also includes the number of local sessions it has created
 * since the last update from Eureka.
 *
 * @author cbarry@kixeye.com
 */
public class SessionLoadBalancer implements LoadBalancer {
    private final static Logger log = LoggerFactory.getLogger(SessionLoadBalancer.class);
    private final LoadingCache<String,LocalSessionCount> incrementalSessions ;

    /**
     * Constructor
     */
    public SessionLoadBalancer() {
        incrementalSessions = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build( new CacheLoader<String, LocalSessionCount>() {
                    @Override
                    public LocalSessionCount load(String key) throws Exception {
                        return new LocalSessionCount();
                    }
                });
    }

    /**
     * Selects a server instance with the smallest number of active sessions.
     *
     * @param serverStats the collection of server instance to choose from
     * @return the server with the fewest sessions
     */
    @Override
    public ServerStats choose(Collection<ServerStats> serverStats) {
        try {
            // Sort servers by number of sessions
            PriorityQueue<SessionServerTuple> pq = new PriorityQueue<>(serverStats.size(), SessionServerTuple.comparator);
            for (ServerStats stats : serverStats) {
                // ignore short-circuited servers
                if (!stats.getServerInstance().isAvailable()) {
                    continue;
                }

                // reset incremental session count if the Eureka data has been updated
                EurekaServerInstance instance = (EurekaServerInstance) stats.getServerInstance();
                LocalSessionCount localSessions = incrementalSessions.get(instance.getId());
                long timestamp = instance.getInstanceInfo().getLastUpdatedTimestamp();
                int additionalSessions = 0;
                if (localSessions.timestamp.get() == timestamp) {
                    additionalSessions = localSessions.sessions.get();
                } else {
                    localSessions.timestamp.set(timestamp);
                    localSessions.sessions.set(0);
                }

                // add server to priority queue to sort by total session count
                pq.add(new SessionServerTuple((ServerStats) stats, additionalSessions, log));
            }
            SessionServerTuple top = pq.peek();
            if (top == null) {
                return null;
            }

            // Assume each return adds an incremental session
            String id = top.stats.getServerInstance().getId();
            incrementalSessions.get(id).sessions.addAndGet(1);

            return top.stats;

        } catch (Exception e) {
            log.error("Unexpected exception in SessionLoadBalancer.choose", e);
            return null;
        }
    }

    /**
     * Internal class tracking the number of local sessions created
     * since the last Eureka refresh.
     */
    static class LocalSessionCount {
        AtomicLong timestamp = new AtomicLong(0);
        AtomicInteger sessions = new AtomicInteger(0);
    }

    /**
     * Internal tuple of the server, session with a comparator for sorting by
     * session count.
     */
    static class SessionServerTuple {
        // sort by session count
        public static Comparator<SessionServerTuple> comparator = new Comparator<SessionServerTuple>() {
            @Override
            public int compare(SessionServerTuple o1, SessionServerTuple o2) {
                return o1.sessions - o2.sessions;
            }
        };

        public ServerStats stats;
        public int sessions;

        public SessionServerTuple(ServerStats stats, int localSessionCount, Logger log) {
            this.stats = stats;
            this.sessions = Integer.MAX_VALUE;

            // Get session count from Eureka meta-data
            Map<String,String> metadata = ((EurekaServerInstance) stats.getServerInstance()).getInstanceInfo().getMetadata();
            if (metadata != null) {
                String strSessions = metadata.get("sessions");
                if (strSessions != null) {
                    try {
                        this.sessions = localSessionCount + Integer.parseInt(strSessions);
                    } catch (Exception e) {
                        log.error("Bad session value: " + strSessions);
                    }
                }
            }
        }
    }
}
