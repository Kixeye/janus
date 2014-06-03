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


import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles creation of a {@link ServerStats} for a given {@link ServerInstance}.
 */
public class ServerStatsFactory<T extends ServerStats> {
    private final Logger logger = LoggerFactory.getLogger(Janus.class);

    private Class<T> typeArgumentClass;

    public ServerStatsFactory(Class<T> typeArgumentClass) {
        this.typeArgumentClass = typeArgumentClass;
    }

    public T createServerStats(MetricRegistry metricRegistry, ServerInstance serverInstance) {
        Preconditions.checkNotNull(metricRegistry, "'metricRegistry cannot be null.'");
        Preconditions.checkNotNull(serverInstance, "'serverInstance cannot be null.'");
        try {
            T stat = typeArgumentClass.newInstance();
            stat.setServerInstance(serverInstance);
            stat.setMetricRegistry(metricRegistry);
            return stat;
        } catch (Exception e) {
            logger.error("Unexpected exception in ServerStatsFactory.createServerStats", e);
            return null;
        }
    }
}
