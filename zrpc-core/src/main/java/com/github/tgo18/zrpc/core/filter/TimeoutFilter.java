package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enforces a per-invocation timeout.
 * Timeout value is read from the URL parameter "timeout" (milliseconds).
 * Default is 3000 ms.
 */
public class TimeoutFilter implements Filter {

    private static final int DEFAULT_TIMEOUT_MS = 3000;

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        int timeoutMs = invoker.getUrl().getIntParameter(URLKeys.TIMEOUT, DEFAULT_TIMEOUT_MS);

        CompletableFuture<RpcResponse> future = invoker.invoke(request);

        // Attach a timeout
        CompletableFuture<RpcResponse> timeoutFuture = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        return timeoutFuture.exceptionally(ex -> {
            if (ex instanceof TimeoutException) {
                throw new RpcException(RpcException.TIMEOUT_EXCEPTION,
                        "RPC invocation timed out after " + timeoutMs + "ms: " + request);
            }
            if (ex instanceof RuntimeException re) throw re;
            throw new RuntimeException(ex);
        });
    }
}
