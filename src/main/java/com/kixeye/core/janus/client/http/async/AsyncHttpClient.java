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
package com.kixeye.core.janus.client.http.async;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.kixeye.core.janus.Janus;
import com.kixeye.core.janus.ServerStats;
import com.kixeye.core.janus.client.exception.NoServerAvailableException;
import com.kixeye.core.janus.client.exception.RetriesExceededException;
import com.kixeye.core.janus.client.http.HttpRequest;
import com.kixeye.core.janus.client.http.HttpResponse;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.*;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An Asynchronous HTTP client which uses Janus for service discovery.
 * 
 * @author ebahtijaragic@kixeye.com
 */
public class AsyncHttpClient implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(AsyncHttpClient.class);
	
	private final Janus janus;
	private final int numRetries;
	private final CloseableHttpAsyncClient httpClient;
	private final ExecutorService executor;

    /**
     * Creates an async http client.
     *
     * @param janus reference to Janus
     * @param numRetries number of retry attempts that should be made in the event of http request error. requests that fail to complete
     *                   are considered errors (ie. IOExceptions) and are eligible for retry. Requests that receive a response (regardless of http status) are
     *                   considered successful and are not eligible for retry.
     */
    public AsyncHttpClient(Janus janus, int numRetries) {
        Preconditions.checkNotNull(janus, "'janus' is required but was null");
        Preconditions.checkArgument(numRetries >= 0, "'numRetries' must be >= 0");

        this.janus = janus;
        this.numRetries = numRetries;
        this.executor = Executors.newCachedThreadPool();
        this.httpClient = HttpAsyncClients.createDefault();

        this.httpClient.start();
    }


	
	/**
	 * Closes this client.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		httpClient.close();
	}

	/**
	 * Executes the given request.
	 * 
	 * @param request the HttpRequest to execute
	 * @param path the path to send the request to.  the path variables should be enclosed with "{}" Ex. /stores/{storeId}/items/{itemId}
	 * @param urlVariables variables that will be substituted into the given path. Order of the given urlVariables is significant. For example,
     *                     variables 5, 10 will be substituted into /stores/{storeId}/items/{itemId} with a result of /stores/5/items/10.
     *
	 * @return ListenableFuture clients can register a listener to be notified when the http request has been completed.
	 * @throws IOException
	 */
	public ListenableFuture<HttpResponse> execute(HttpRequest request, String path, Object... urlVariables) throws IOException {
		SettableFuture<HttpResponse> response = SettableFuture.create();
		executor.submit(new ExecuteTask(response, request, path, urlVariables, janus, httpClient, executor, numRetries));
		return response;
	}
	
	private static class ExecuteTask implements Runnable {
		private final Janus janus;
		private final HttpAsyncClient httpClient;
		private final ExecutorService executor;
		private final SettableFuture<HttpResponse> response;
		private final HttpRequest request;
		private final String url;
		private final Object[] urlVariables;
		
		private final Runnable self;

		private final int maxRetryCount;
		private int retryCount;
		
		/**
		 * @param responseFuture the response future
		 * @param request the http request
		 * @param maxRetryCount maximum number of retries
		 * @param path the http request path
		 * @param urlVariables path substitution variables
		 */
		public ExecuteTask(SettableFuture<HttpResponse> responseFuture, HttpRequest request, String path, Object[] urlVariables,
				Janus janus, HttpAsyncClient httpClient, ExecutorService executor, int maxRetryCount) {
			this.response = responseFuture;
			this.request = request;
			this.url = path;
			this.urlVariables = urlVariables.clone();
			this.janus = janus;
			this.httpClient = httpClient;
			this.executor = executor;
			this.maxRetryCount = maxRetryCount;
			
			this.self = this;
			this.retryCount = 0;
		}

		public void run() {
			try {
				final ServerStats server = janus.getServer();
				if (server == null) {
					throw new NoServerAvailableException( janus.getServiceName() );
				}
	
				URI formattedUrl = new UriTemplate(server.getServerInstance().getUrl() + (url.startsWith("/") ? "" : "/") + url).expand(urlVariables);
	
				// map to the required type
				HttpUriRequest httpClientRequest = null;
				
				switch (request.getMethod()) {
					case DELETE:
						httpClientRequest = new HttpDelete(formattedUrl);
						break;
					case GET:
						httpClientRequest = new HttpGet(formattedUrl);
						break;
					case HEAD:
						httpClientRequest = new HttpHead(formattedUrl);
						break;
					case OPTIONS:
						httpClientRequest = new HttpOptions(formattedUrl);
						break;
					case PATCH:
						httpClientRequest = new HttpPatch(formattedUrl);
						break;
					case POST:
						httpClientRequest = new HttpPost(formattedUrl);
						break;
					case PUT:
						httpClientRequest = new HttpPut(formattedUrl);
						break;
					case TRACE:
						httpClientRequest = new HttpTrace(formattedUrl);
						break;
					default:
						break;
				}
				
				// set the body
				if (httpClientRequest instanceof HttpEntityEnclosingRequest) {
					((HttpEntityEnclosingRequest)httpClientRequest).setEntity(new InputStreamEntity(request.getBody()));
				}
				
				// set the headers
				for (String headerName : request.getHeaderNames()) {
					for (String headerValue : request.getHeader(headerName)) {
						httpClientRequest.addHeader(headerName, headerValue);
					}
				}
				
				// execute!
				FutureCallback<org.apache.http.HttpResponse> completeListener = new FutureCallback<org.apache.http.HttpResponse>() {
                    private long startTime = System.currentTimeMillis();
                    
					@Override
					public void failed(Exception ex) {
						if (retryCount >= maxRetryCount) {
		                	response.setException(new RetriesExceededException(janus.getServiceName(), maxRetryCount));
		                } else {
		                	logger.warn("Error while processing request, will retry", ex);
		                	retryCount++;
		                	executor.submit(self);
		                }
						
		                server.incrementErrors();
		                
		                server.decrementOpenRequests();
					}
					
					@Override
					public void completed(org.apache.http.HttpResponse result) {
                        long latency = System.currentTimeMillis() - startTime;

						Map<String, Collection<String>> headers = new HashMap<>();
						for (Header header : result.getAllHeaders()) {
							Collection<String> headerValues = headers.get(header.getName());
							if (headerValues == null) {
								headerValues = Lists.newArrayList();
								headers.put(header.getName(), headerValues);
							}
							
							headerValues.add(header.getValue());
						}
						
						try {
							response.set(new HttpResponse(result.getStatusLine().getStatusCode(), headers, result.getEntity().getContent()));
						} catch (Exception e) {
                            logger.debug("Passing exception to response", e);
		                	response.setException(e);
						}

		                server.decrementOpenRequests();
                        server.recordLatency(latency);
					}
					
					@Override
					public void cancelled() {
						response.set(null);
					}
				};
				
				httpClient.execute(httpClientRequest, completeListener);
	
		        server.incrementSentMessages();
		        server.incrementOpenRequests();
			} catch (Exception e) {
                logger.debug("Passing exception to response", e);
				response.setException(e);
			}
		}
	}
}
