package com.github.tgo18.zrpc.admin.model;

public class ProviderInfo {
    private String id;          // host:port
    private String host;
    private int port;
    private String serviceName;
    private String version;
    private String group;
    private String protocol;
    private int weight;
    private boolean enabled;
    private String application;

    public ProviderInfo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApplication() { return application; }
    public void setApplication(String application) { this.application = application; }
}
