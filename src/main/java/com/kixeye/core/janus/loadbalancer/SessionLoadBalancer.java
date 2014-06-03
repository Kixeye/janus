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
 * This is a Eureka specific load balancer which selects a server instance with the fewest number of active
 * sessions.
 *
 * A server instance's session count comes from a meta-data field called 'sessions' associated with the instance in Eureka.
 * Each server instance reports its session count with Eureka periodically.  Since the server instance list may be
 * cached on the client side, this strategy takes into account the number of sessions created per server instance from the
 * client since the last time its server instance cache was refreshed. This allows for more accurate load balancing between cache refresh
 * intervals.
 *
 * @author cbarry@kixeye.com
 */
public class SessionLoadBalancer implements LoadBalancer<ServerStats> {
    private final static Logger log = LoggerFactory.getLogger(SessionLoadBalancer.class);
    private final LoadingCache<String,IncrementalSession> incrementalSessions ;

    /**
     * Constructor
     */
    public SessionLoadBalancer() {
        incrementalSessions = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build( new CacheLoader<String, IncrementalSession>() {
                    @Override
                    public IncrementalSession load(String key) throws Exception {
                        return new IncrementalSession();
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
            PriorityQueue<Server> pq = new PriorityQueue<>(serverStats.size(), Server.comparator);
            for (ServerStats stats : serverStats) {
                if (!stats.getServerInstance().isAvailable()) {
                    continue;
                }
                if (stats.getServerInstance() instanceof EurekaServerInstance) {
                    String id = stats.getServerInstance().getId();
                    IncrementalSession incrementalSession = incrementalSessions.get(id);
                    pq.add(new Server(stats, incrementalSession, log));
                }
            }
            Server top = pq.peek();
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

    static class IncrementalSession {
        AtomicLong timestamp = new AtomicLong(0);
        AtomicInteger sessions = new AtomicInteger(0);
    }

    static class Server {
        // sort by session count
        public static Comparator<Server> comparator = new Comparator<Server>() {
            @Override
            public int compare(Server o1, Server o2) {
                return o1.sessions - o2.sessions;
            }
        };

        public ServerStats stats;
        public int sessions;

        public Server(ServerStats stats, IncrementalSession incrementalSessions, Logger log) {
            this.stats = stats;
            this.sessions = Integer.MAX_VALUE;

            // Get incremental sessions on top of base Eureka value.
            // Reset if we have a new value from Eureka.
            EurekaServerInstance instance = (EurekaServerInstance) stats.getServerInstance();
            long timestamp = instance.getInstanceInfo().getLastUpdatedTimestamp();
            int additionalSessions = 0;
            if (incrementalSessions.timestamp.get() == timestamp) {
                additionalSessions = incrementalSessions.sessions.get();
            } else {
                incrementalSessions.timestamp.set(timestamp);
                incrementalSessions.sessions.set(0);
            }

            // Get session count from Eureka
            Map<String,String> metadata = instance.getInstanceInfo().getMetadata();
            if (metadata != null) {
                String strSessions = metadata.get("sessions");
                if (strSessions != null) {
                    try {
                        this.sessions = additionalSessions + Integer.parseInt(strSessions);
                    } catch (Exception e) {
                        log.error("Bad session value: " + strSessions);
                    }
                }
            }
        }
    }
}
