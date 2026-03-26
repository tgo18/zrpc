package com.github.tgo18.zrpc.core.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an RPC invocation request.
 */
public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final AtomicLong REQUEST_ID_GEN = new AtomicLong(0);

    /** Unique request ID for request-response correlation */
    private final long requestId;

    /** Fully qualified service interface name */
    private String serviceName;

    /** Service version, e.g. "1.0.0" */
    private String version;

    /** Service group */
    private String group;

    /** Method name to invoke */
    private String methodName;

    /** Parameter types for method resolution */
    private Class<?>[] parameterTypes;

    /** Actual arguments */
    private Object[] arguments;

    /** Attachments: extra metadata (tracing headers, auth tokens, etc.) */
    private Map<String, String> attachments = new HashMap<>();

    /** Whether this is a one-way call (fire-and-forget) */
    private boolean oneWay;

    public RpcRequest() {
        this.requestId = REQUEST_ID_GEN.incrementAndGet();
    }

    public RpcRequest(long requestId) {
        this.requestId = requestId;
    }

    public long getRequestId() { return requestId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public Class<?>[] getParameterTypes() { return parameterTypes; }
    public void setParameterTypes(Class<?>[] parameterTypes) { this.parameterTypes = parameterTypes; }

    public Object[] getArguments() { return arguments; }
    public void setArguments(Object[] arguments) { this.arguments = arguments; }

    public Map<String, String> getAttachments() { return attachments; }
    public void setAttachments(Map<String, String> attachments) { this.attachments = attachments; }

    public void setAttachment(String key, String value) { attachments.put(key, value); }
    public String getAttachment(String key) { return attachments.get(key); }
    public String getAttachment(String key, String defaultValue) {
        return attachments.getOrDefault(key, defaultValue);
    }

    public boolean isOneWay() { return oneWay; }
    public void setOneWay(boolean oneWay) { this.oneWay = oneWay; }

    /**
     * Returns a human-readable service key for routing purposes.
     */
    public String getServiceKey() {
        StringBuilder sb = new StringBuilder(serviceName);
        if (group != null && !group.isEmpty()) sb.insert(0, group + "/");
        if (version != null && !version.isEmpty()) sb.append(":").append(version);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RpcRequest{id=" + requestId +
                ", service=" + getServiceKey() +
                ", method=" + methodName + "}";
    }
}
