package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Logs every RPC invocation: service, method, elapsed time, and result status.
 */
public class AccessLogFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("zrpc.access");

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        long start = System.currentTimeMillis();
        return invoker.invoke(request).whenComplete((response, ex) -> {
            long elapsed = System.currentTimeMillis() - start;
            if (ex != null) {
                log.info("[FAIL] {} {} elapsed={}ms error={}",
                        request.getServiceName(), request.getMethodName(), elapsed, ex.getMessage());
            } else {
                log.info("[OK]   {} {} elapsed={}ms status={}",
                        request.getServiceName(), request.getMethodName(), elapsed,
                        response != null ? response.getStatus() : "null");
            }
        });
    }
}
