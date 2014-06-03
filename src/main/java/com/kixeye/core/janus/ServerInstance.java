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

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicLongProperty;
import com.netflix.config.DynamicPropertyFactory;
import scala.annotation.meta.getter;

/**
 * Represents a server instance within a service cluster. Used to communicate information about the
 * instance such as urls, ports, protocol etc...
 * <p/>
 * If a particular server instance starts experiencing too many errors, the instance can be temporarily short-circuited by calling the tripCircuitBreaker() method. The
 * amount of time an instance will remain short-circuited is defaulted to 30 seconds and can be configured by setting/updating the property "janus.shortCircuitDuration".
 *
 * If tripCircuitBreaker() is called on a {@link ServerInstance} that is already tripped, the amount of time that the server will be short-circuited will increase exponentially based
 * on the number of times it was tripped.
 *
 * @author cbarry@kixeye.com
 */
public class ServerInstance {
    public static final String SHORT_CIRCUIT_DURATION = "janus.shortCircuitDuration";
    public static final DynamicLongProperty shortCircuitDuration = DynamicPropertyFactory.getInstance().getLongProperty(SHORT_CIRCUIT_DURATION, 30000);

    private boolean isSecure;
    private boolean available;
    private boolean lastAvailable;
    private String serviceName;
    private String id;
    private String host;
    private int port;
    private int websocketPort;
    private String url;

    private ServerInstanceListener listener;

    private volatile boolean shortCircuited;
    private volatile long shortCircuitExpiration;
    private volatile long shortCircuitCount;

    /**
     *
     * @param serviceName the name of the service cluster
     * @param url the url of the server instance
     */
    public ServerInstance(String serviceName, String url) {
        HostPortInfo info = getHostInfoFromUrl(url);
        if (info.websocket) {
            initialize(serviceName, null, info.host, info.secure, -1, info.port);
        } else {
            initialize(serviceName, null, info.host, info.secure, info.port, -1);
        }
    }

    /**
     *
     * @param serviceName the name of the service cluster
     * @param id the id of the server instance
     * @param host the host of the service instance
     * @param isSecure whether or not the server instance support secure connections (HTTPS/WSS etc...)
     * @param port http port the server instance is listening on
     * @param websocketPort web socket port the server instance is listening on
     */
    public ServerInstance(String serviceName, String id, String host, boolean isSecure, int port, int websocketPort) {
        initialize(serviceName, id, host, isSecure, port, websocketPort);
    }

    /**
     * setter for shortCircuitDuration
     * @param shortCircuitDuration the duration
     */
    public static void setShortCircuitDuration(long shortCircuitDuration){
        ConfigurationManager.getConfigInstance().setProperty(SHORT_CIRCUIT_DURATION, shortCircuitDuration);
    }

    /**
     * getter for shortCircuitDuration
     * @return the shortCircuitDuration
     */
    public static long getShortCircuitDuration(){
        return shortCircuitDuration.get();
    }

    /**
     * Allow periodic bookkeeping of the instance.
     *
     * @return false of the server should be removed, true otherwise
     */
    public boolean tick() {
        // notify listener if availability has changed
        boolean currentlyAvailable = isAvailable();
        if (currentlyAvailable != lastAvailable) {
            lastAvailable = currentlyAvailable;
            if (listener != null) {
                listener.onAvailabilityChange(currentlyAvailable);
            }
        }
        return !isExpired();
    }

    /**
     * getter for listener
     * @return the listener
     */
    public ServerInstanceListener getListener() {
        return this.listener;
    }

    /**
     * setter for listener
     * @param listener a {@link ServerInstanceListener} that will get notified of server availability change. It can be null.
     */
    public void setListener(ServerInstanceListener listener) {
        this.listener = listener;
    }

    /**
     * Is this instance available to service requests?
     *
     * @return isAvailable
     */
    public boolean isAvailable() {
        return !isExpired() && !isShortCircuited() && available;
    }

    /**
     * Is this instance currently in short circuit mode?
     *
     * @return whether or not the server instance is currently short-circuited
     */
    public boolean isShortCircuited() {
        if (shortCircuited) {
            long delta = System.currentTimeMillis() - shortCircuitExpiration;
            if (delta >= 0) {
                shortCircuited = false;
            }
        }
        return shortCircuited;
    }

    /**
     * Is this instance initialized with secure ports?
     *
     * @return whether or not the server instance is secure
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Is this instance expired, i.e. has not received a recently update.
     *
     * @return whether or not the server instance is expired
     */
    public boolean isExpired() {
        return false;
    }

    /**
     * setter for available
     * @param isAvailable available or not?
     */
    public void setAvailable(boolean isAvailable) {
        available = isAvailable;
    }

    /**
     * trip the circuit breaker on the server instance. if already tripped,
     * the amount of time the server will remain tripped will be increased
     * exponentially.
     */
    public void tripCircuitBreaker() {
        final long now = System.currentTimeMillis();

        // increment short circuit count for back off calculation
        if ((now - shortCircuitExpiration) > shortCircuitDuration.get()) {
            shortCircuitCount = 0;
        } else {
            shortCircuitCount = Math.max(shortCircuitCount + 1, 5);
        }

        // set time out using exponential back off
        long timeout = (long) (Math.pow(1.5, shortCircuitCount) * shortCircuitDuration.get());
        shortCircuitExpiration = now + timeout;
        shortCircuited = true;
    }

    /**
     * Get the amount of time until the server instance's circuit breaker is
     * no longer tripped.
     * @return remaining time
     */
    public double getCircuitBreakerRemainingTime() {
        if (isShortCircuited()) {
            long delta = shortCircuitExpiration - System.currentTimeMillis();
            if (delta > 0) {
                return delta * 1000.0;
            }
        }
        return 0.0;
    }

    /**
     * getter for id
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * getter for host
     * @return host
     */
    public String getHost() {
        return host;
    }

    /**
     * getter for port
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * getter for websocketPort
     * @return websocketPort
     */
    public int getWebsocketPort() {
        return websocketPort;
    }

    /**
     * getter for url
     * @return url
     */
    public String getUrl() {
        return url;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + serviceName.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServerInstance that = (ServerInstance) o;

        if (!id.equals(that.id)) {
            return false;
        }
        if (!serviceName.equals(that.serviceName)) {
            return false;
        }

        return true;
    }

    private void initialize(String serviceName, String id, String host, boolean isSecure, int port, int websocketPort) {
        this.serviceName = serviceName;
        this.host = host;
        this.isSecure = isSecure;
        this.port = port;
        this.websocketPort = websocketPort;
        if (id != null) {
            this.id = id;
        } else {
            this.id = host + ":" + ((port > 0) ? port : websocketPort);
        }
        if (isSecure) {
            this.url = String.format("https://%s:%d", host, port);
        } else {
            this.url = String.format("http://%s:%d", host, port);
        }
    }

    private static HostPortInfo getHostInfoFromUrl(String url) {
        boolean websocket = false;
        boolean secure = false;

        if (url.toLowerCase().startsWith("http://")) {
            websocket = false;
            secure = false;
            url = url.substring(7);
        } else if (url.toLowerCase().startsWith("https://")) {
            websocket = false;
            secure = true;
            url = url.substring(8);
        } else if (url.toLowerCase().startsWith("ws://")) {
            websocket = true;
            secure = false;
            url = url.substring(5);
        } else if (url.toLowerCase().startsWith("wss://")) {
            websocket = true;
            secure = true;
            url = url.substring(6);
        }

        if (url.contains("/")) {
            int slashIdx = url.indexOf('/');
            url = url.substring(0, slashIdx);
        }

        String host;
        int port = -1;
        int colonIdx = url.indexOf(':');
        if (colonIdx == -1) {
            host = url; // default
            port = 80;
        } else {
            host = url.substring(0, colonIdx);
            port = Integer.valueOf(url.substring(colonIdx + 1));
        }
        return new HostPortInfo(websocket, secure, host, port);
    }

    private static class HostPortInfo {
        private final boolean websocket;
        private final boolean secure;
        private final String host;
        private final int port;

        HostPortInfo(boolean websocket, boolean secure, String host, int port) {
            this.websocket = websocket;
            this.secure = secure;
            this.host = host;
            this.port = port;
        }
    }
}
