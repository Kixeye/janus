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
package com.kixeye.janus.serverlist;

import com.kixeye.janus.ServerInstance;
import com.kixeye.scout.eureka.EurekaServiceInstanceDescriptor;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * A {@link ServerInstance} which adds additional meta-data provided by Eureka
 * about server instances.
 *
 * @author cbarry@kixeye.com
 */
public class EurekaServerInstance extends ServerInstance {

    private EurekaServiceInstanceDescriptor instanceInfo;

    // track time since instance has been updated
    private long lastUpdateTime = System.currentTimeMillis();
    private DynamicLongProperty propInstanceTimeout = DynamicPropertyFactory.getInstance().getLongProperty("janus.InstanceTimeOutInMillis", 125000);

    public EurekaServerInstance(String serviceName, String url) {
        super(serviceName, url);
    }

    public EurekaServerInstance(String serviceName, String id, String host, boolean secure, int port, int websocketPort) {
        super(serviceName, id, host, secure, port, websocketPort);
    }

    public EurekaServerInstance(String serviceName, String id, String host, boolean secure, int port, int websocketPort, EurekaServiceInstanceDescriptor instanceInfo) {
        super(serviceName, id, host, secure, port, websocketPort);
        this.instanceInfo = instanceInfo;
    }

    public EurekaServiceInstanceDescriptor getInstanceInfo() {
        return instanceInfo;
    }

    public boolean isExpired() {
        return (lastUpdateTime + propInstanceTimeout.get()) < System.currentTimeMillis();
    }

    @Override
    public void setAvailable(boolean isAvailable) {
        lastUpdateTime = System.currentTimeMillis();
        super.setAvailable(isAvailable);
    }
}
