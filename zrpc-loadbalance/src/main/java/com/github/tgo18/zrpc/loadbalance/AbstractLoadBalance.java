package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.loadbalance.LoadBalance;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;

/**
 * Base class for load balancing strategies.
 * Handles null/single-element fast paths, delegates to {@link #doSelect}.
 */
public abstract class AbstractLoadBalance implements LoadBalance {

    @Override
    public final <T> Invoker<T> select(List<Invoker<T>> invokers, RpcRequest request) {
        if (invokers == null || invokers.isEmpty()) {
            throw new IllegalStateException("No available invokers");
        }
        if (invokers.size() == 1) return invokers.get(0);
        return doSelect(invokers, request);
    }

    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request);
}
