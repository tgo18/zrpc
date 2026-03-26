package com.github.tgo18.zrpc.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * zRPC Spring Boot configuration properties.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * zrpc:
 *   application:
 *     name: my-service
 *   protocol:
 *     name: zrpc         # zrpc | grpc | http2
 *     port: 20880
 *     serialization: json
 *   registry:
 *     type: zookeeper    # memory | zookeeper | nacos
 *     address: 127.0.0.1:2181
 *   consumer:
 *     timeout: 3000
 *     retries: 2
 *     loadbalance: roundrobin
 * </pre>
 */
@ConfigurationProperties(prefix = "zrpc")
public class ZRpcProperties {

    private ApplicationConfig application = new ApplicationConfig();
    private ProtocolConfig protocol = new ProtocolConfig();
    private RegistryConfig registry = new RegistryConfig();
    private ConsumerConfig consumer = new ConsumerConfig();

    public ApplicationConfig getApplication() { return application; }
    public void setApplication(ApplicationConfig application) { this.application = application; }

    public ProtocolConfig getProtocol() { return protocol; }
    public void setProtocol(ProtocolConfig protocol) { this.protocol = protocol; }

    public RegistryConfig getRegistry() { return registry; }
    public void setRegistry(RegistryConfig registry) { this.registry = registry; }

    public ConsumerConfig getConsumer() { return consumer; }
    public void setConsumer(ConsumerConfig consumer) { this.consumer = consumer; }

    // --- Nested config classes ---

    public static class ApplicationConfig {
        private String name = "default";
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class ProtocolConfig {
        private String name = "zrpc";
        private String host = "0.0.0.0";
        private int port = 20880;
        private String serialization = "json";
        private int threads = Runtime.getRuntime().availableProcessors() * 2;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getSerialization() { return serialization; }
        public void setSerialization(String serialization) { this.serialization = serialization; }
        public int getThreads() { return threads; }
        public void setThreads(int threads) { this.threads = threads; }
    }

    public static class RegistryConfig {
        private String type = "memory";
        private String address = "";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class ConsumerConfig {
        private int timeout = 3000;
        private int retries = 2;
        private String loadbalance = "roundrobin";

        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
        public String getLoadbalance() { return loadbalance; }
        public void setLoadbalance(String loadbalance) { this.loadbalance = loadbalance; }
    }
}
