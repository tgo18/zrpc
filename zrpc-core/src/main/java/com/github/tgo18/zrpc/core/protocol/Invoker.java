package com.github.tgo18.zrpc.core.protocol;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;

import java.util.concurrent.CompletableFuture;

/**
 * Core abstraction representing a callable endpoint — either a remote service
 * stub (consumer side) or a local service implementation (provider side).
 *
 * <p>The Invoker is the central concept in zRPC, analogous to Dubbo's Invoker.
 * All protocol implementations, cluster strategies, and filters operate on Invokers.
 *
 * @param <T> service interface type
 */
public interface Invoker<T> {

    /** The service interface class this invoker represents */
    Class<T> getInterface();

    /** The URL describing where/how this invoker connects */
    URL getUrl();

    /** Whether this invoker is currently available for invocation */
    boolean isAvailable();

    /**
     * Perform the invocation, returning a future response.
     * The response future completes exceptionally on network or protocol errors.
     */
    CompletableFuture<RpcResponse> invoke(RpcRequest request);

    /** Gracefully shut down this invoker and release resources */
    void destroy();
}
