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
 * Thrown when the maximum number of retries has been exhausted while
 * attempting to perform a remote action against a service cluster.
 *
 * @author cbarry@kixeye.com
 */
public class RetriesExceededException extends JanusException {
    private static final long serialVersionUID = -8676518862491148374L;

    public RetriesExceededException(String service, long retries) {
        super(String.format("Max retries <%d> for service <%s> was exceeded", retries, service));
    }
}
