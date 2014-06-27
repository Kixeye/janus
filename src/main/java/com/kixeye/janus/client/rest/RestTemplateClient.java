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

import com.kixeye.janus.client.exception.NoServerAvailableException;
import com.kixeye.janus.client.exception.RetriesExceededException;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * A client for make RESTful http requests.
 *
 * @author cbarry@kixeye.com
 */
public interface RestTemplateClient {

    /**
     * Performs a synchronous http GET request, returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to request
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> T getForObject(String path, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http GET request, substituting the given urlVariables into the given path,
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
    public <T> T getForObject(String path, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http GET request, substituting the given urlVariables into the given path,
     * and returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to request
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @param pathVariables variables that will substituted (by name) into the given path. For example,
     *                     variables "storeId=1,itemId=4" will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> T getForObject(String path, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
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
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
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
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
     * and returning an object of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @param pathVariables variables that will substituted (by name) into the given path. For example,
     *                     variables "storeId=1,itemId=4" will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> T postForObject(String path, final Object requestBody, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
     * and returning {@link ResponseEntity} containing an instance of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
     * and returning a {@link ResponseEntity} containing an instance of the given responseType converted from the response payload's body
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
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http POST request, substituting the given urlVariables into the given path,
     * and returning a {@link ResponseEntity} containing an instance of the given responseType converted from the response payload's body
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param responseType the type of object to convert the response body to. it is assumed that the
     *                     implementation knows how to make this conversion.
     * @param pathVariables variables that will substituted (by name) into the given path. For example,
     *                     variables "storeId=1,itemId=4" will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @return an instance of responseType corresponding to the http response body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public <T> ResponseEntity<T> postForEntity(String path, final Object requestBody, final Class<T> responseType, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http PUT request, storing the given request body at the given path.
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void put(String path, final Object requestBody) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http PUT request, storing the given request body at the given path.
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void put(String path, final Object requestBody, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http PUT request, storing the given request body at the given path.
     *
     * @param path path to the resource to create
     * @param requestBody an object that will be converted and sent as the request body
     * @param pathVariables variables that will substituted (by name) into the given path. For example,
     *                     variables "storeId=1,itemId=4" will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void put(String path, final Object requestBody, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http DELETE request, deleting the resource at the given path.
     *
     * @param path path to the resource to create
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void delete(String path) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http DELETE request, deleting the resource at the given path.
     *
     * @param path path to the resource to create
     * @param pathVariables variables that will substituted into the given path in the order they appear. For example,
     *                     variables 1, 4 will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void delete(String path, final Object... pathVariables) throws NoServerAvailableException, RetriesExceededException;

    /**
     * Performs a synchronous http DELETE request, deleting the resource at the given path.
     *
     * @param path path to the resource to create
     * @param pathVariables variables that will substituted (by name) into the given path. For example,
     *                     variables "storeId=1,itemId=4" will be substituted into path /stores/{storeId}/items/{itemId} as
     *                     /stores/1/items/4.
     * @throws NoServerAvailableException if no server instance could be found for the given request
     * @throws RetriesExceededException if the maximum number of retries was exceeded
     */
    public void delete(String path, final Map<String, ?> pathVariables) throws NoServerAvailableException, RetriesExceededException;
}
