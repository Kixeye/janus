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
package com.kixeye.core.janus;

import static com.codahale.metrics.MetricRegistry.name;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.*;
import com.google.common.base.Preconditions;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 *  The default {@link ServerStats} implementation used by Janus
 *
 *  @see ServerStats
 *
 *  @author cbarry@kixeye.com
 */
public class ServerStats implements ServerStats {
    protected final DynamicLongProperty propErrorThreshold = DynamicPropertyFactory.getInstance().getLongProperty("janus.errorThresholdPerSec", 5);

    protected MetricRegistry metrics;
    protected ServerInstance server;

    // reported metrics
    protected Counter openRequestCounter;
    protected Counter openSessionsCounter;
    protected Meter errorMeter;
    protected Meter sentMessageMeter;
    protected Meter receivedMessageMeter;
    protected Counter circuitBreakerTrippedCounter;
    protected Gauge<Double> circuitBreakerTimeGauge;
    protected Histogram latencyHistogram;
    
    protected String objectId = UUID.randomUUID().toString();

    // internal metric tracking
    protected SlidingTimeWindowReservoir errorsPerSecond;

    /**
     *
     * @param metricRegistry registry containing the metrics
     */
    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metrics = Preconditions.checkNotNull(metricRegistry, "'metricRegistry' cannot be null.");
        this.openRequestCounter = metrics.counter(name(server.getId(), "open-requests"));
        this.openSessionsCounter = metrics.counter(name(server.getId(), "open-sessions"));
        this.sentMessageMeter = metrics.meter(name(server.getId(), "sent-messages"));
        this.receivedMessageMeter = metrics.meter(name(server.getId(), "received-messages"));
        this.errorMeter = metrics.meter(name(server.getId(), "errors"));
        this.circuitBreakerTrippedCounter = metrics.counter(name(server.getId(),"short-circuit-tripped"));
        this.circuitBreakerTimeGauge = new Gauge<Double>() {
            @Override
            public Double getValue() {
                if (server != null) {
                    return server.getCircuitBreakerRemainingTime();
                } else {
                    return 0.0;
                }
            }
        };
        this.metrics.register(name(objectId, server.getId(),"short-circuit-time-remaining"), circuitBreakerTimeGauge);
        this.latencyHistogram = new Histogram( new SlidingWindowReservoir(100) );
        this.metrics.register(name(objectId, server.getId(), "latency"), this.latencyHistogram);

        // internal metrics
        this.errorsPerSecond = new SlidingTimeWindowReservoir(1, TimeUnit.SECONDS);
    }

    /**
     * setter for serverInstance
     * @param serverInstance the serverInstance
     */
    @Override
    public void setServerInstance(ServerInstance serverInstance) {
        this.server = serverInstance;
    }

    /**
     * getter for serverInstance
     * @return serverInstance
     */
    @Override
    public ServerInstance getServerInstance() {
        return server;
    }

    /**
     * increment the number of open requests to a server instance by a {@link Janus} instance
     */
    public void incrementOpenRequests() {
        openRequestCounter.inc();
    }

    /**
     * decrement the number of open requests to a server instance by a {@link Janus} instance
     */
    public void decrementOpenRequests() {
        openRequestCounter.dec();
    }

    /**
     * get the number of open requests to a server instance by a {@link Janus} instance
     */
    public long getOpenRequestCount() {
        return openRequestCounter.getCount();
    }

    /**
     * record the amount of latency for a request/message to a server instance by a {@link Janus} instance
     */
    public void recordLatency(long latencyInMs) {
        latencyHistogram.update(latencyInMs);
    }

    /**
     * increment the number of open sessions to a server instance by a {@link Janus} instance
     */
    public void incrementOpenSessions() {
        openSessionsCounter.inc();
    }

    /**
     * decrement the number of open sessions to a server instance by a {@link Janus} instance
     */
    public void decrementOpenSessions() {
        openSessionsCounter.dec();
    }

    /**
     * get the number of open sessions to a server instance by a {@link Janus} instance
     */
    public long getOpenSessionsCount() {
        return openSessionsCounter.getCount();
    }

    /**
     * increment the number of errors from a server instance by a {@link Janus} instance
     */
    public void incrementErrors() {
        errorMeter.mark();
        errorsPerSecond.update(1);

        // should we short circuit the server?
        if ( errorsPerSecond.size() >= propErrorThreshold.get()) {
            circuitBreakerTrippedCounter.inc();
            server.tripCircuitBreaker();
        }
    }

    /**
     *
     * increment the number of messages sent to a server instance by a {@link Janus} instance
     */
    public void incrementSentMessages() {
        sentMessageMeter.mark();
    }

    /**
     * get the rate of messages sent (per second) to a server instance by a {@link Janus} instance
     */
    public double getSentMessagesPerSecond() {
        return sentMessageMeter.getOneMinuteRate();
    }

    /**
     * increment the number of message received from a server instance by a {@link Janus} instance
     */
    public void incrementReceivedMessages() {
        receivedMessageMeter.mark();
    }

    /**
     * get the rate of message received (per second) from a server instance by a {@link Janus} instance
     * @return received messages per second
     */
    public double getReceivedMessagesPerSecond() {
        return receivedMessageMeter.getOneMinuteRate();
    }

    @Override
    public int hashCode() {
        return server.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServerStats that = (ServerStats) o;

        if (!server.equals(that.server)) {
            return false;
        }

        return true;
    }
}
