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

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.common.base.Charsets;
import com.kixeye.core.transport.dto.Envelope;
import com.kixeye.core.transport.serde.SerDeConfiguration;
import com.kixeye.core.transport.serde.converter.JsonMessageSerDe;
import com.netflix.config.ConfigurationManager;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/")
@Configuration
@Import(SerDeConfiguration.class)
public class TestRestService extends DelegatingWebMvcConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TestRestService.class);

    @Autowired
    private JsonMessageSerDe jsonMessageSerDe;

    @RequestMapping(value = "/test_no_params", method = {RequestMethod.GET})
    public String getTest() {
        return "pong";
    }

    @RequestMapping(value = "/test_no_params", method = {RequestMethod.POST})
    public String postTest(@RequestBody String message) {
        return message;
    }

    @RequestMapping(value = "/test_params/{test}", method = {RequestMethod.GET})
    public String getTest(@PathVariable String test) {
        return test;
    }

    @RequestMapping(value = "/test_params/{test}", method = {RequestMethod.POST})
    public String postTest(@PathVariable String test, @RequestBody String message) {
        return test + message;
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
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

    @Bean(initMethod = "start", destroyMethod = "stop")
    @Order(0)
    public Server httpServer(
            @Value("${http.enabled:false}") boolean httpEnabled,
            @Value("${http.hostname:}") String httpHostname,
            @Value("${http.port:-1}") int httpPort,

            @Value("${https.enabled:false}") boolean httpsEnabled,
            @Value("${https.hostname:}") String httpsHostname,
            @Value("${https.port:-1}") int httpsPort,
            @Value("${https.selfSigned:false}") boolean selfSigned,
            @Value("${https.mutualSsl:false}") boolean mutualSsl,

            @Value("${https.keyStorePath:}") String keyStorePath,
            @Value("${https.keyStoreData:}") String keyStoreData,
            @Value("${https.keyStorePassword:}") String keyStorePassword,
            @Value("${https.keyManagerPassword:}") String keyManagerPassword,

            @Value("${https.trustStorePath:}") String trustStorePath,
            @Value("${https.trustStoreData:}") String trustStoreData,
            @Value("${https.trustStorePassword:}") String trustStorePassword,

            @Value("${https.excludedCipherSuites:}") String[] excludedCipherSuites,

            ConfigurableWebApplicationContext webApplicationContext) throws Exception {

        // set up servlets
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
        context.setErrorHandler(null);
        context.setWelcomeFiles(new String[]{"/"});

        // set up spring with the servlet context
        setServletContext(context.getServletContext());

        // configure the spring mvc dispatcher
        DispatcherServlet dispatcher = new DispatcherServlet(webApplicationContext);

        // enable gzip
        context.addFilter(GzipFilter.class, "/*", null);

        // map application servlets
        context.addServlet(new ServletHolder(dispatcher), "/");
        // create the server
        Server server = new Server();

        server.setHandler(context);


        // set up connectors
        if (httpEnabled) {
            InetSocketAddress address = StringUtils.isBlank(httpHostname) ? new InetSocketAddress(httpPort) : new InetSocketAddress(httpHostname, httpPort);

            registerHttpConnector(server, address);
        }

        if (httpsEnabled) {
            InetSocketAddress address = StringUtils.isBlank(httpsHostname) ? new InetSocketAddress(httpsPort) : new InetSocketAddress(httpsHostname, httpsPort);

            registerHttpsConnector(server, address, selfSigned, mutualSsl, keyStorePath, keyStoreData, keyStorePassword, keyManagerPassword,
                    trustStorePath, trustStoreData, trustStorePassword, excludedCipherSuites);
        }

        return server;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @Order(0)
    public Server webSocketServer(
            @Value("${websocket.enabled:false}") boolean websocketEnabled,
            @Value("${websocket.hostname:}") String websocketHostname,
            @Value("${websocket.port:-1}") int websocketPort,

            @Value("${secureWebsocket.enabled:false}") boolean secureWebsocketEnabled,
            @Value("${secureWebsocket.hostname:}") String secureWebsocketHostname,
            @Value("${secureWebsocket.port:-1}") int secureWebsocketPort,
            @Value("${secureWebsocket.selfSigned:false}") boolean selfSigned,
            @Value("${secureWebsocket.mutualSsl:false}") boolean mutualSsl,

            @Value("${secureWebsocket.keyStorePath:}") String keyStorePath,
            @Value("${secureWebsocket.keyStoreData:}") String keyStoreData,
            @Value("${secureWebsocket.keyStorePassword:}") String keyStorePassword,
            @Value("${secureWebsocket.keyManagerPassword:}") String keyManagerPassword,

            @Value("${secureWebsocket.trustStorePath:}") String trustStorePath,
            @Value("${secureWebsocket.trustStoreData:}") String trustStoreData,
            @Value("${secureWebsocket.trustStorePassword:}") String trustStorePassword,

            @Value("${securewebsocket.excludedCipherSuites:}") String[] excludedCipherSuites) throws Exception {
        // set up servlets
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
        context.setErrorHandler(null);
        context.setWelcomeFiles(new String[]{"/"});


        // create the websocket creator
        final WebSocketCreator webSocketCreator = new WebSocketCreator() {
            public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
                return new WebSocketListener() {
                    private Session session;

                    @Override
                    public void onWebSocketBinary(byte[] payload, int offset, int len) {
                        try {
                            Envelope envelope = jsonMessageSerDe.deserialize(payload, offset, len, Envelope.class);
                            envelope = new Envelope(envelope.action, envelope.typeId, envelope.transactionId, ByteBuffer.wrap(jsonMessageSerDe.serialize(new PongMessage("pong"))));
                            session.getRemote().sendBytes(ByteBuffer.wrap(jsonMessageSerDe.serialize(envelope)));
                        } catch (Exception e) {
                            logger.error("onWebSocketBinary", e);
                        }
                    }

                    @Override
                    public void onWebSocketClose(int statusCode, String reason) {

                    }

                    @Override
                    public void onWebSocketConnect(Session session) {
                        this.session = session;
                    }

                    @Override
                    public void onWebSocketError(Throwable cause) {
                        logger.error("error on websocket server", cause);
                    }

                    @Override
                    public void onWebSocketText(String message) {
                        byte[] data = message.getBytes(Charsets.UTF_8);
                        onWebSocketBinary(data, 0, data.length);
                    }
                };
            }
        };

        // configure the websocket servlet
        ServletHolder webSocketServlet = new ServletHolder(new WebSocketServlet() {
            private static final long serialVersionUID = -3022799271546369505L;

            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator(webSocketCreator);
            }
        });

        Map<String, String> webSocketProperties = new HashMap<>();
        AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        Iterator<String> webSocketPropertyKeys = config.getKeys("websocket");
        while (webSocketPropertyKeys.hasNext()) {
            String key = webSocketPropertyKeys.next();

            webSocketProperties.put(key.replaceFirst(Pattern.quote("websocket."), ""), config.getString(key));
        }

        webSocketServlet.setInitParameters(webSocketProperties);

        context.addServlet(webSocketServlet, "/" + jsonMessageSerDe.getMessageFormatName() + "/*");

        // create the server
        Server server = new Server();

        server.setHandler(context);


        // set up connectors
        if (websocketEnabled) {
            InetSocketAddress address = StringUtils.isBlank(websocketHostname) ? new InetSocketAddress(websocketPort) : new InetSocketAddress(websocketHostname, websocketPort);

            registerHttpConnector(server, address);
        }

        if (secureWebsocketEnabled) {
            InetSocketAddress address = StringUtils.isBlank(secureWebsocketHostname) ? new InetSocketAddress(secureWebsocketPort) : new InetSocketAddress(secureWebsocketHostname, secureWebsocketPort);

            registerHttpsConnector(server, address, selfSigned, mutualSsl, keyStorePath, keyStoreData, keyStorePassword, keyManagerPassword,
                    trustStorePath, trustStoreData, trustStorePassword, excludedCipherSuites);
        }

        return server;
    }


    static public AnnotationConfigWebApplicationContext createContext(int httpPort, int sockPort) {
        Map<String, Object> properties = new HashMap<>();
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
        context.register(TestRestService.class);
        context.refresh();
        return context;
    }


    static public AnnotationConfigWebApplicationContext createSecureContext(int httpPort, int sockPort, int secureSockPort) {
        Map<String, Object> properties = new HashMap<>();
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
        context.register(TestRestService.class);
        context.refresh();
        return context;
    }

    private static void registerHttpConnector(Server server, InetSocketAddress address) {
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(address.getHostName());
        connector.setPort(address.getPort());

        server.addConnector(connector);
    }

    private static void registerHttpsConnector(Server server, InetSocketAddress address, boolean selfSigned,
                                               boolean mutualSsl, String keyStorePath, String keyStoreData, String keyStorePassword, String keyManagerPassword,
                                               String trustStorePath, String trustStoreData, String trustStorePassword, String[] excludedCipherSuites) throws Exception {
        // SSL Context Factory
        SslContextFactory sslContextFactory = new SslContextFactory();

        if (selfSigned) {
            char[] passwordChars = UUID.randomUUID().toString().toCharArray();

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

            keyStore.load(null, passwordChars);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();

            v3CertGen.setSerialNumber(BigInteger.valueOf(new SecureRandom().nextInt()).abs());
            v3CertGen.setIssuerDN(new X509Principal("CN=" + "kixeye.com" + ", OU=None, O=None L=None, C=None"));
            v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
            v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365 * 10)));
            v3CertGen.setSubjectDN(new X509Principal("CN=" + "kixeye.com" + ", OU=None, O=None L=None, C=None"));

            v3CertGen.setPublicKey(keyPair.getPublic());
            v3CertGen.setSignatureAlgorithm("MD5WithRSAEncryption");

            X509Certificate privateKeyCertificate = v3CertGen.generateX509Certificate(keyPair.getPrivate());

            keyStore.setKeyEntry("selfSigned", keyPair.getPrivate(), passwordChars,
                    new java.security.cert.Certificate[]{privateKeyCertificate});

            ByteArrayOutputStream keyStoreBaos = new ByteArrayOutputStream();
            keyStore.store(keyStoreBaos, passwordChars);

            keyStoreData = new String(Hex.encode(keyStoreBaos.toByteArray()), Charsets.UTF_8);
            keyStorePassword = new String(passwordChars);
            keyManagerPassword = keyStorePassword;

            sslContextFactory.setTrustAll(true);
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (StringUtils.isNotBlank(keyStoreData)) {
            keyStore.load(new ByteArrayInputStream(Hex.decode(keyStoreData)), keyStorePassword.toCharArray());
        } else if (StringUtils.isNotBlank(keyStorePath)) {
            try (InputStream inputStream = new DefaultResourceLoader().getResource(keyStorePath).getInputStream()) {
                keyStore.load(inputStream, keyStorePassword.toCharArray());
            }
        }

        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(keyStorePassword);
        if (StringUtils.isBlank(keyManagerPassword)) {
            keyManagerPassword = keyStorePassword;
        }
        sslContextFactory.setKeyManagerPassword(keyManagerPassword);
        KeyStore trustStore = null;
        if (StringUtils.isNotBlank(trustStoreData)) {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(new ByteArrayInputStream(Hex.decode(trustStoreData)), trustStorePassword.toCharArray());
        } else if (StringUtils.isNotBlank(trustStorePath)) {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream inputStream = new DefaultResourceLoader().getResource(trustStorePath).getInputStream()) {
                trustStore.load(inputStream, trustStorePassword.toCharArray());
            }
        }
        if (trustStore != null) {
            sslContextFactory.setTrustStore(trustStore);
            sslContextFactory.setTrustStorePassword(trustStorePassword);
        }
        sslContextFactory.setNeedClientAuth(mutualSsl);
        sslContextFactory.setExcludeCipherSuites(excludedCipherSuites);

        // SSL Connector
        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.toString()),
                new HttpConnectionFactory()
        );
        connector.setHost(address.getHostName());
        connector.setPort(address.getPort());

        server.addConnector(connector);
    }

}
