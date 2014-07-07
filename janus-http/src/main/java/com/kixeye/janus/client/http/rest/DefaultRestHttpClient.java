package com.kixeye.janus.client.http.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kixeye.janus.Janus;
import com.kixeye.janus.ServerStats;
import com.kixeye.janus.client.exception.NoServerAvailableException;
import com.kixeye.janus.client.exception.RetriesExceededException;
import com.kixeye.relax.AsyncRestClient;
import com.kixeye.relax.HttpResponse;
import com.kixeye.relax.RestClientSerDe;
import com.kixeye.relax.RestClients;

/**
 * A REST client that uses the {@link AsyncRestClient}
 * 
 * @author ebahtijaragic
 */
public class DefaultRestHttpClient {
	private static final Logger logger = LoggerFactory.getLogger(DefaultRestHttpClient.class);
	
	private static final String USER_AGENT_NAME = "Janus" + DefaultRestHttpClient.class.getSimpleName();

	private final AsyncRestClient client;
	private final Janus janus;
	private final int numRetries;
	private final String contentType;
	
	/**
	 * Creates a new HTTP client with a JSON serializer.
	 * 
	 * @param janus
	 * @param numRetries
	 */
	public DefaultRestHttpClient(Janus janus, int numRetries) {
		assert janus != null : "'janus' cannot be null.";
		assert numRetries >= 0 : "'numRetries' must be >= 0";
        
		this.janus = janus;
		this.numRetries = numRetries;
		this.contentType = null;
		
		this.client = (AsyncRestClient)RestClients
				.create(JACKSON_JSON_SER_DE)
				.withUserAgentName(USER_AGENT_NAME)
				.build();
	}
	
	/**
	 * Creates a new HTTP client with the given serializer.
	 * 
	 * @param janus
	 * @param numRetries
	 * @param serDe
	 * @param contentType
	 */
	public DefaultRestHttpClient(Janus janus, int numRetries, RestClientSerDe serDe, String contentType) {
		assert janus != null : "'janus' cannot be null.";
		assert numRetries >= 0 : "'numRetries' must be >= 0";
		assert serDe != null : "'serDe' cannot be null.";
		
		this.janus = janus;
		this.numRetries = numRetries;
		this.contentType = contentType;
		
		this.client = (AsyncRestClient)RestClients
				.create(serDe)
				.withUserAgentName(USER_AGENT_NAME)
				.build();
	}
	
	/**
	 * Creates a new HTTP client with the given serializer and request config.
	 * 
	 * @param janus
	 * @param numRetries
	 * @param serDe
	 * @param contentType
	 * @param requestConfig
	 */
	public DefaultRestHttpClient(Janus janus, int numRetries, RestClientSerDe serDe, String contentType, RequestConfig requestConfig) {
		assert janus != null : "'janus' cannot be null.";
		assert numRetries >= 0 : "'numRetries' must be >= 0";
		assert serDe != null : "'serDe' cannot be null.";
		assert requestConfig != null : "'requestConfig' cannot be null.";
		
		this.janus = janus;
		this.numRetries = numRetries;
		this.contentType = contentType;
		
		this.client = (AsyncRestClient)RestClients
				.create(serDe)
				.withRequestConfig(requestConfig)
				.withUserAgentName(USER_AGENT_NAME)
				.build();
	}
	
	/**
	 * Creates a new HTTP clien t with the given serializer, request config, and SSL context.
	 * @param janus
	 * @param numRetries
	 * @param serDe
	 * @param contentType
	 * @param requestConfig
	 * @param sslContext
	 */
	public DefaultRestHttpClient(Janus janus, int numRetries, RestClientSerDe serDe, String contentType, RequestConfig requestConfig, SSLContext sslContext) {
		assert janus != null : "'janus' cannot be null.";
		assert numRetries >= 0 : "'numRetries' must be >= 0";
		assert serDe != null : "'serDe' cannot be null.";
		assert requestConfig != null : "'requestConfig' cannot be null.";
		assert sslContext != null : "'sslContext' cannot be null.";
		
		this.janus = janus;
		this.numRetries = numRetries;
		this.contentType = contentType;
		
		this.client = (AsyncRestClient)RestClients
				.create(serDe)
				.withRequestConfig(requestConfig)
				.withSSLContext(sslContext)
				.withUserAgentName(USER_AGENT_NAME)
				.build();
	}
	
	/**
     * Performs a asynchronous http GET request, returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to request
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> HttpResponse<T> get(String path, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public HttpResponse<T> execute(String url) throws Exception {
                return client.get(url, contentType, responseType).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http GET request, substituting the given urlVariables into the given path,
     * and returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to request
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> HttpResponse<T> get(String path, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public HttpResponse<T> execute(String url) throws Exception {
                return client.get(url, contentType, responseType, pathVariables).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http POST request, substituting the given urlVariables into the given path,
     * and returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> HttpResponse<T> post(String path, final Object requestBody, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public HttpResponse<T> execute(String url) throws Exception {
                return client.post(url, contentType, contentType, requestBody, responseType).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http POST request, substituting the given urlVariables into the given path,
     * and returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> HttpResponse<T> post(String path, final Object requestBody, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<T> wrapped = new FunctionWrapper<T>() {
            @Override
            public HttpResponse<T> execute(String url) throws Exception {
                return client.post(url, contentType, contentType, requestBody, responseType, pathVariables).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        return executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http PUT request, storing the given request body at the given path.
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void put(String path, final Object requestBody) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Void> wrapped = new FunctionWrapper<Void>() {
            @Override
            public HttpResponse<Void> execute(String url) throws Exception {
                return client.put(url, contentType, contentType, requestBody).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http PUT request, storing the given request body at the given path.
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void put(String path, final Object requestBody, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Void> wrapped = new FunctionWrapper<Void>() {
            @Override
            public HttpResponse<Void> execute(String url) throws Exception {
                return client.put(url, contentType, contentType, requestBody, pathVariables).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http DELETE request, deleting the resource at the given path.
     *
     * @param path path to the resource to create
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void delete(String path) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Void> wrapped = new FunctionWrapper<Void>() {
            @Override
            public HttpResponse<Void> execute(String url) throws Exception {
                return client.delete(url).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }

    /**
     * Performs a asynchronous http DELETE request, deleting the resource at the given path.
     *
     * @param path path to the resource to create
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void delete(String path, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException {
        FunctionWrapper<Void> wrapped = new FunctionWrapper<Void>() {
            @Override
            public HttpResponse<Void> execute(String url) throws Exception {
                return client.delete(url, pathVariables).waitForComplete(10, TimeUnit.SECONDS).get();
            }
        };
        executeWithLoadBalancer(path, wrapped);
    }
	
    /**
     * Executes a function with load balancer.
     * 
     * @param path
     * @param function
     * @return
     * @throws NoServerAvailableException
     * @throws RetriesExceededException
     */
	private <T> HttpResponse<T> executeWithLoadBalancer(String path, FunctionWrapper<T> function) throws NoServerAvailableException, RetriesExceededException {
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
            HttpResponse<T> result = null;
            long latency = -1;
            try {
                server.incrementSentMessages();
                server.incrementOpenRequests();
                long startTime = System.currentTimeMillis();
                result = function.execute(newUrl);
                latency = System.currentTimeMillis() - startTime;

                // exit if successful
                if (result == null) {
                	throw new TimeoutException("Timed out while waiting for a response.");
                } else {
                	if (result.getStatusCode() >= 500) {
                		throw new HttpResponseException(result.getStatusCode(), "Unexpected response");
                	}
                	
                    return result;
                }
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

            retries -= 1;
        } while (retries >= 0);

        throw new RetriesExceededException(janus.getServiceName(), numRetries);
    }
	
	/**
	 * A JSON SerDe that uses Jackson.
	 */
	public static final RestClientSerDe JACKSON_JSON_SER_DE = new RestClientSerDe() {
		private final byte[] EMPTY = new byte[0];
		
		private ObjectMapper objectMapper = new ObjectMapper();
		
		/**
		 * @see com.kixeye.relax.RestClientSerDe#serialize(java.lang.String, java.lang.Object)
		 */
		public byte[] serialize(String mimeType, Object obj) throws IOException {
			return obj == null ? EMPTY : objectMapper.writeValueAsBytes(obj);
		}

		/**
		 * @see com.kixeye.relax.RestClientSerDe#deserialize(java.lang.String, byte[], int, int, java.lang.Class)
		 */
		public <T> T deserialize(String mimeType, byte[] data, int offset, int length, Class<T> clazz) throws IOException {
			return objectMapper.readValue(data, offset, length, clazz);
		}
	};
	
	/**
	 * A String SerDe.
	 */
	public static final RestClientSerDe UTF8_STRING_SER_DE = new RestClientSerDe() {
		private final byte[] EMPTY = new byte[0];
		
		/**
		 * @see com.kixeye.relax.RestClientSerDe#serialize(java.lang.String, java.lang.Object)
		 */
		public byte[] serialize(String mimeType, Object obj) throws IOException {
			return obj == null ? EMPTY : obj.toString().getBytes(StandardCharsets.UTF_8);
		}

		/**
		 * @see com.kixeye.relax.RestClientSerDe#deserialize(java.lang.String, byte[], int, int, java.lang.Class)
		 */
		@SuppressWarnings("unchecked")
		public <T> T deserialize(String mimeType, byte[] data, int offset, int length, Class<T> clazz) throws IOException {
			return (T)new String(data, offset, length, StandardCharsets.UTF_8);
		}
	};
	
	private interface FunctionWrapper<T> {
        HttpResponse<T> execute(String url) throws Exception;
    }
}
