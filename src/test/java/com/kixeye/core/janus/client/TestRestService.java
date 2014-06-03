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
package com.kixeye.core.janus.client;

import com.kixeye.core.transport.TransportConfiguration;
import com.kixeye.core.transport.websocket.ActionMapping;
import com.kixeye.core.transport.websocket.WebSocketController;
import com.kixeye.core.transport.websocket.WebSocketMessageRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@RestController
@WebSocketController
@RequestMapping("/")
public class TestRestService {
    @Autowired
    private WebSocketMessageRegistry messageRegistry;

    @PostConstruct
    public void initialize() {
        messageRegistry.registerType("ping", PingMessage.class);
        messageRegistry.registerType("pong", PongMessage.class);
    }

    @RequestMapping(value="/test_no_params", method={ RequestMethod.GET })
    public String getTest() {
        return "pong";
    }

    @RequestMapping(value="/test_no_params", method={ RequestMethod.POST })
    public String postTest( @RequestBody String message) {
        return message;
    }

    @RequestMapping(value="/test_params/{test}", method={ RequestMethod.GET })
    public String getTest( @PathVariable String test ) {
        return test;
    }

    @RequestMapping(value="/test_params/{test}", method={ RequestMethod.POST})
    public String postTest( @PathVariable String test, @RequestBody String message ) {
        return test + message;
    }

    @ActionMapping("ping")
    public PongMessage webSocketPing() {
        return new PongMessage("pong");
    }

    static public class PingMessage {
    }

    static public class PongMessage {
        public String messsage;

        public PongMessage() {
        }

        public PongMessage(String message) {
            this.messsage = message;
        }
    }

    static public AnnotationConfigWebApplicationContext createContext( int httpPort, int sockPort ) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("http.enabled", "true");
        properties.put("http.port", "" + httpPort);
        properties.put("http.hostname", "localhost");
        properties.put("websocket.enabled", "true");
        properties.put("websocket.port", "" + sockPort);
        properties.put("websocket.hostname", "localhost");

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("default", properties));
        context.setEnvironment(environment);
        context.register(PropertySourcesPlaceholderConfigurer.class);
        context.register(TransportConfiguration.class);
        context.register(TestRestService.class);
        context.refresh();
        return context;
    }


    static public AnnotationConfigWebApplicationContext createSecureContext( int httpPort, int sockPort, int secureSockPort ) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("http.enabled", "true");
        properties.put("http.port", "" + httpPort);
        properties.put("http.hostname", "localhost");

        properties.put("websocket.enabled", "true");
        properties.put("websocket.port", "" + sockPort);
        properties.put("websocket.hostname", "localhost");

        properties.put("secureWebsocket.enabled", "true");
        properties.put("secureWebsocket.port", "" + secureSockPort);
        properties.put("secureWebsocket.hostname", "localhost");
        properties.put("secureWebsocket.selfSigned", "true");

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("default", properties));
        context.setEnvironment(environment);
        context.register(PropertySourcesPlaceholderConfigurer.class);
        context.register(TransportConfiguration.class);
        context.register(TestRestService.class);
        context.refresh();
        return context;
    }
}
