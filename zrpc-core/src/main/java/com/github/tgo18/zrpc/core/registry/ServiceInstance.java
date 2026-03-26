package com.github.tgo18.zrpc.core.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single service instance registered in the service registry.
 */
public class ServiceInstance {

    private String serviceName;
    private String host;
    private int port;
    private String protocol;
    private String version;
    private String group;
    private int weight = 100;
    private boolean enabled = true;
    private Map<String, String> metadata = new HashMap<>();

    public ServiceInstance() {}

    public ServiceInstance(String serviceName, String host, int port) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
    }

    public String getId() {
        return host + ":" + port;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public String getMetadata(String key) { return metadata.get(key); }
    public void putMetadata(String key, String value) { metadata.put(key, value); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceInstance si)) return false;
        return port == si.port &&
                Objects.equals(serviceName, si.serviceName) &&
                Objects.equals(host, si.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, host, port);
    }

    @Override
    public String toString() {
        return "ServiceInstance{" + serviceName + "@" + host + ":" + port +
                ", protocol=" + protocol + ", version=" + version + "}";
    }
}
