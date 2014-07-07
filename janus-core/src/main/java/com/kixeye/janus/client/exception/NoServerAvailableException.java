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
package com.kixeye.janus.client.exception;

/**
 * Thrown when there is no server available in a service cluster to perform a
 * remote action.
 *
 * @author cbarry@kixeye.com
 */
public class NoServerAvailableException extends JanusException {
	private static final long serialVersionUID = -9020001261444581468L;

	public NoServerAvailableException(String service) {
        super("No servers available for service <" + service + ">");
    }
}
