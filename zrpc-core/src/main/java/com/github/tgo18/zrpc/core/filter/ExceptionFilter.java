package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Catches exceptions thrown during invocation and wraps them into RpcResponse.
 * Should be placed at the outermost position in the provider filter chain.
 */
public class ExceptionFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ExceptionFilter.class);

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        return invoker.invoke(request).exceptionally(ex -> {
            log.error("RPC invocation failed: {}", request, ex);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return RpcResponse.error(request.getRequestId(), cause);
        });
    }
}
