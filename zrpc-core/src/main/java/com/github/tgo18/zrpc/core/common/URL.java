package com.github.tgo18.zrpc.core.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uniform Resource Locator for service addressing.
 * Format: protocol://host:port/service?key=value&key2=value2
 * Inspired by Dubbo's URL model.
 */
public final class URL {

    private final String protocol;
    private final String host;
    private final int port;
    private final String path;           // service interface name
    private final Map<String, String> parameters;

    // Cached string representation
    private volatile String string;

    private URL(Builder builder) {
        this.protocol = builder.protocol;
        this.host = builder.host;
        this.port = builder.port;
        this.path = builder.path;
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static URL valueOf(String url) {
        Objects.requireNonNull(url, "url");
        String protocol = null;
        String host = null;
        int port = 0;
        String path = null;
        Map<String, String> parameters = new HashMap<>();

        int i = url.indexOf("://");
        if (i >= 0) {
            protocol = url.substring(0, i);
            url = url.substring(i + 3);
        }

        i = url.indexOf('?');
        if (i >= 0) {
            String[] parts = url.substring(i + 1).split("&");
            for (String part : parts) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    parameters.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
            url = url.substring(0, i);
        }

        i = url.indexOf('/');
        if (i >= 0) {
            path = url.substring(i + 1);
            url = url.substring(0, i);
        }

        i = url.lastIndexOf(':');
        if (i >= 0) {
            port = Integer.parseInt(url.substring(i + 1));
            host = url.substring(0, i);
        } else {
            host = url;
        }

        return new URL(new Builder()
                .protocol(protocol)
                .host(host)
                .port(port)
                .path(path)
                .parameters(parameters));
    }

    public String getProtocol() { return protocol; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public Map<String, String> getParameters() { return parameters; }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    public int getIntParameter(String key, int defaultValue) {
        String value = parameters.get(key);
        if (value == null || value.isEmpty()) return defaultValue;
        return Integer.parseInt(value);
    }

    public boolean getBooleanParameter(String key, boolean defaultValue) {
        String value = parameters.get(key);
        if (value == null || value.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public URL addParameter(String key, String value) {
        Map<String, String> map = new HashMap<>(this.parameters);
        map.put(key, value);
        return new URL(new Builder()
                .protocol(protocol).host(host).port(port).path(path).parameters(map));
    }

    public URL addParameters(Map<String, String> params) {
        Map<String, String> map = new HashMap<>(this.parameters);
        map.putAll(params);
        return new URL(new Builder()
                .protocol(protocol).host(host).port(port).path(path).parameters(map));
    }

    public String getAddress() {
        return port <= 0 ? host : host + ":" + port;
    }

    public String getServiceKey() {
        return path != null ? path : getParameter(URLKeys.INTERFACE);
    }

    @Override
    public String toString() {
        if (string != null) return string;
        StringBuilder sb = new StringBuilder();
        if (protocol != null) sb.append(protocol).append("://");
        if (host != null) sb.append(host);
        if (port > 0) sb.append(':').append(port);
        if (path != null && !path.isEmpty()) sb.append('/').append(path);
        if (!parameters.isEmpty()) {
            sb.append('?');
            parameters.forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
            sb.deleteCharAt(sb.length() - 1);
        }
        string = sb.toString();
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof URL url)) return false;
        return port == url.port &&
                Objects.equals(protocol, url.protocol) &&
                Objects.equals(host, url.host) &&
                Objects.equals(path, url.path) &&
                Objects.equals(parameters, url.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, host, port, path, parameters);
    }

    public static final class Builder {
        private String protocol;
        private String host;
        private int port;
        private String path;
        private final Map<String, String> parameters = new ConcurrentHashMap<>();

        public Builder protocol(String protocol) { this.protocol = protocol; return this; }
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder parameter(String key, String value) { parameters.put(key, value); return this; }
        public Builder parameters(Map<String, String> params) { parameters.putAll(params); return this; }

        public URL build() { return new URL(this); }
    }
}
