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
package com.kixeye.janus.client.rest;

import com.google.common.base.Preconditions;
import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.client.exception.NoServerAvailableException;
import com.kixeye.janus.client.exception.RetriesExceededException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * A {@link RestTemplateClient} which uses Spring's {@link RestTemplate} for making synchronous HTTP requests.
 *
 * @author cbarry@kixeye.com
 */
public class DefaultRestTemplateClient implements RestTemplateClient {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRestTemplateClient.class);

    private final Object emptyResult = new Object();

    private final Janus janus;
    private final RestTemplate client;
    private final int numRetries;

    /**
     *
     * @param janus the Janus instance
     * @param numRetries maximum number of retries
     */
    public DefaultRestTemplateClient(Janus janus, int numRetries) {
        this(janus, numRetries, new HttpComponentsClientHttpRequestFactory());
    }

    /**
     *
     * @param janus the Janus instance
     * @param numRetries maximum number of retries
     * @param httpRequestFactory a factory object for constructing {@link org.springframework.http.client.ClientHttpRequest}s
     */
    public DefaultRestTemplateClient(Janus janus, int numRetries, ClientHttpRequestFactory httpRequestFactory) {
        Preconditions.checkNotNull(janus, "'janus' is cannot be null.");
        Preconditions.checkArgument(numRetries >= 0, "'numRetries' must be >= 0");
        Preconditions.checkNotNull(httpRequestFactory, "'httpRequestFactory' is cannot be null.");

        this.janus = janus;
        this.numRetries = numRetries;
        this.client = new RestTemplate(httpRequestFactory);
    }

    /**
     * getter for client
     * @return {@link RestTemplate}
     */
    public RestTemplate getRestTemplate() {
        return client;
    }

    /**
     * @see {@link RestTemplateClient#getForObject(String, Class)}
     */
    public <T> T getForObject(String path, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) {
                return client.getForObject(url, responseType);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#getForObject(String, Class, Object...)}
     */
    public <T> T getForObject(String path, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) {
                return client.getForObject(url, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#getForObject(String, Class, java.util.Map)}
     */
    public <T> T getForObject(String path, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) throws RestClientException {
                return client.getForObject(url, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForObject(String, Object, Class)}
     */
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) throws RestClientException {
                return client.postForObject(url, requestBody, responseType);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForObject(String, Object, Class, Object...)}
     */
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) {
                return client.postForObject(url, requestBody, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForObject(String, Object, Class, java.util.Map)}
     */
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public T execute(String url) throws RestClientException {
                return client.postForObject(url, requestBody, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForEntity(String, Object, Class, Object...)}
     */
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<ResponseEntity<T>> wrapped = new FunctionWrapper<ResponseEntity<T>>() {
            @Override
            public ResponseEntity<T> execute(String url) throws RestClientException {
                return client.postForEntity(url, requestBody, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForEntity(String, Object, Class, java.util.Map)}
     */
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<ResponseEntity<T>> wrapped = new FunctionWrapper<ResponseEntity<T>>() {
            @Override
            public ResponseEntity<T> execute(String url) throws RestClientException {
                return client.postForEntity(url, requestBody, responseType, pathVariables);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#postForEntity(String, Object, Class)}
     */
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<ResponseEntity<T>> wrapped = new FunctionWrapper<ResponseEntity<T>>() {
            @Override
            public ResponseEntity<T> execute(String url) throws RestClientException {
                return client.postForEntity(url, requestBody, responseType);
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#put(String, Object)}
     */
    public void put(String path, final Object requestBody) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) throws RestClientException {
                client.put(url, requestBody);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#put(String, Object, Object...)}
     */
    public void put(String path, final Object requestBody, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) throws RestClientException {
                client.put(url, requestBody, pathVariables);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#put(String, Object, java.util.Map)}
     */
    public void put(String path, final Object requestBody, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) throws RestClientException {
                client.put(url, requestBody, pathVariables);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#delete(String)}
     */
    public void delete(String path) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) throws RestClientException {
                client.delete(url);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#delete(String, Object...)}
     */
    public void delete(String path, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) {
                client.delete(url, pathVariables);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * @see {@link RestTemplateClient#delete(String, java.util.Map)}
     */
    public void delete(String path, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Object> wrapped = new FunctionWrapper<Object>() {
            @Override
            public Object execute(String url) {
                client.delete(url, pathVariables);
                return emptyResult;
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    private <T> T executeWithLoadBalancer(String path, FunctionWrapper<T> function) throws NoServerAvailableException, RetriesExceededException {
        long retries = numRetries;
        do {
            // get a load balanced server
            ServerStats server = janus.getServer();
            if (server == null) {
                throw new NoServerAvailableException(janus.getServiceName());
            }

            // prefix URL with selected server
            String newUrl = server.getServerInstance().getUrl() + path;

            // call into REST Template wrapper
            T result = null;
            long latency = -1;
            try {
                server.incrementSentMessages();
                server.incrementOpenRequests();
                long startTime = System.currentTimeMillis();
                result = function.execute(newUrl);
                latency = System.currentTimeMillis() - startTime;
            } catch (HttpClientErrorException e) {
                // client errors are NOT retried and instead passed back to the caller.
                throw e;
            } catch (HttpServerErrorException e) {
                // server errors are retried and can trigger a short circuit.
                logger.debug("Received HttpServerErrorException, retrying another server", e);
                server.incrementErrors();
            } catch (Exception e) {
                // unexpected exception, treat as a server problem but also log it.
                logger.warn("RestClient threw unexpected exception, retrying another server", e);
                server.incrementErrors();
            } finally {
                server.decrementOpenRequests();
                if (latency > 0) {
                    server.recordLatency(latency);
                }
            }

            // exit if successful
            if (result != null) {
                return result;
            }

            retries -= 1;
        } while (retries >= 0);

        throw new RetriesExceededException(janus.getServiceName(), numRetries);
    }

    private interface FunctionWrapper<T> {
        T execute(String url);
    }
}
