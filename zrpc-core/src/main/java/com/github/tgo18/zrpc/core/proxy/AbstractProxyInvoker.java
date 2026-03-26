package com.github.tgo18.zrpc.core.proxy;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Base Invoker that wraps a local service implementation.
 * Used on the provider side to bridge framework invocations to POJO methods.
 */
public abstract class AbstractProxyInvoker<T> implements Invoker<T> {

    private final T impl;
    private final Class<T> type;
    private final URL url;

    protected AbstractProxyInvoker(T impl, Class<T> type, URL url) {
        this.impl = impl;
        this.type = type;
        this.url = url;
    }

    @Override public Class<T> getInterface() { return type; }
    @Override public URL getUrl() { return url; }
    @Override public boolean isAvailable() { return true; }
    @Override public void destroy() {}

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        try {
            Object result = doInvoke(impl, request.getMethodName(),
                    request.getParameterTypes(), request.getArguments());

            // Support async service implementations
            if (result instanceof CompletableFuture<?> f) {
                return f.thenApply(v -> RpcResponse.success(request.getRequestId(), v))
                        .exceptionally(ex -> RpcResponse.error(request.getRequestId(), ex));
            }
            return CompletableFuture.completedFuture(
                    RpcResponse.success(request.getRequestId(), result));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    RpcResponse.error(request.getRequestId(), e));
        }
    }

    protected abstract Object doInvoke(T proxy, String methodName,
                                        Class<?>[] parameterTypes, Object[] args) throws Exception;
}
