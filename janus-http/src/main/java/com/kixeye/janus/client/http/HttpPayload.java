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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;

/**
 * Represents an HTTP payload.
 * 
 * @author ebahtijaragic@kixeye.com
 */
public abstract class HttpPayload {

    private ListMultimap<String, String> headers = Multimaps.synchronizedListMultimap(Multimaps.newListMultimap(
			Maps.<String, Collection<String>> newTreeMap(),
			new Supplier<LinkedList<String>>() {
				public LinkedList<String> get() {
					return Lists.newLinkedList();
				}
			}));

	private InputStream body = null;

	/**
	 * @param headers http request/response headers
	 * @param body http request/response body
	 */
	public HttpPayload(Map<String, Collection<String>> headers, InputStream body) {
		if (headers != null) {
			for (Entry<String, Collection<String>> entry : headers.entrySet()) {
				this.headers.putAll(entry.getKey(), entry.getValue());
			}
		}
		this.body = body;
	}
	
	/**
	 * @param body the body of the payload as a stream
	 */
	public HttpPayload(InputStream body) {
		this.body = body;
	}

	/**
	 * Gets the header names.
	 * 
	 * @return header names
	 */
	public Set<String> getHeaderNames() {
		return headers.keySet();
	}
	
	/**
	 * @return the headers
	 */
	public List<String> getHeader(String name) {
		return headers.get(name);
	}

	/**
	 * @return the body
	 */
	public InputStream getBody() {
		return body;
	}

}
