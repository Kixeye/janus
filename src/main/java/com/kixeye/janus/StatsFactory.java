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

/**
 * Factory class responsible for creating instances of ServerStats.
 *
 * @author dturner@kixeye.com
 */
public interface StatsFactory {

    /**
     * Create an instance of {@link ServerStats} for the given {@link ServerInstance}
     * @param serverInstance the {@link ServerInstance} to create stats for
     * @return instance of {@link ServerStats}
     */
    ServerStats createServerStats(ServerInstance serverInstance);
}
