package com.github.tgo18.zrpc.core.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-local RPC context propagating metadata across filter chains and transports.
 * Similar to Dubbo's RpcContext.
 */
public class RpcContext {

    private static final ThreadLocal<RpcContext> LOCAL = ThreadLocal.withInitial(RpcContext::new);

    private URL localAddress;
    private URL remoteAddress;
    private RpcRequest request;
    private RpcResponse response;
    private boolean isProviderSide;
    private boolean isConsumerSide;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> attachments = new HashMap<>();

    /** Returns the context for the current thread */
    public static RpcContext getContext() {
        return LOCAL.get();
    }

    /** Removes the context for the current thread (call after RPC completes) */
    public static void removeContext() {
        LOCAL.remove();
    }

    public void setLocalAddress(URL localAddress) { this.localAddress = localAddress; }
    public URL getLocalAddress() { return localAddress; }

    public void setRemoteAddress(URL remoteAddress) { this.remoteAddress = remoteAddress; }
    public URL getRemoteAddress() { return remoteAddress; }

    public void setRequest(RpcRequest request) { this.request = request; }
    public RpcRequest getRequest() { return request; }

    public void setResponse(RpcResponse response) { this.response = response; }
    public RpcResponse getResponse() { return response; }

    public boolean isProviderSide() { return isProviderSide; }
    public void setProviderSide(boolean providerSide) { isProviderSide = providerSide; }

    public boolean isConsumerSide() { return isConsumerSide; }
    public void setConsumerSide(boolean consumerSide) { isConsumerSide = consumerSide; }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) { return (T) attributes.get(key); }

    public void setAttachment(String key, String value) { attachments.put(key, value); }
    public String getAttachment(String key) { return attachments.get(key); }
    public Map<String, String> getAttachments() { return attachments; }

    public void clearAttachments() { attachments.clear(); }
    public void clearAttributes() { attributes.clear(); }
}
