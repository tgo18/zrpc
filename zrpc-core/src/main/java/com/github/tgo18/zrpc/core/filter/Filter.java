package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.extension.SPI;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Filter SPI — intercepts RPC invocations for cross-cutting concerns.
 *
 * <p>Filters form a chain. Each filter calls {@code invoker.invoke(request)} (or
 * the next filter via the chain) to continue the call. Built-in filters:
 * <ul>
 *   <li>{@code accesslog} — logs every invocation</li>
 *   <li>{@code metrics} — records latency and error rate</li>
 *   <li>{@code timeout} — enforces per-invocation timeout</li>
 *   <li>{@code retry} — automatic retry on failure</li>
 *   <li>{@code tps} — TPS limiting</li>
 *   <li>{@code tracing} — OpenTelemetry context propagation</li>
 *   <li>{@code exception} — normalizes exceptions into RpcResponse</li>
 * </ul>
 */
@SPI
public interface Filter {

    /**
     * Intercept the invocation.
     *
     * @param invoker next invoker in the chain
     * @param request the current RPC request
     * @return response future
     */
    CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request);
}
