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
package com.kixeye.janus.client.http;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import com.google.common.base.Preconditions;

/**
 * Represents an http request.
 * 
 * @author ebahtijaragic@kixeye.com
 */
public class HttpRequest extends HttpPayload {
	private HttpMethod method;
	
	/**
	 * @param method the http method of the request
	 * @param headers the headers of the request
	 * @param body the body of the request
	 */
	public HttpRequest(HttpMethod method, Map<String, Collection<String>> headers, InputStream body) {
		super(headers, body);
		
		setMethod(method);
	}

	/**
	 * @return the method
	 */
	public HttpMethod getMethod() {
		return method;
	}

	/**
	 * @param method the method to set
	 */
	public void setMethod(HttpMethod method) {
		Preconditions.checkNotNull(method, "'method' cannot be null");
		
		this.method = method;
	}
}
