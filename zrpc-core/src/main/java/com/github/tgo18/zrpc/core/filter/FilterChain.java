package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a filter chain wrapping an Invoker.
 *
 * <p>Given filters [A, B, C] and invoker X, the call path is:
 * <pre>A -> B -> C -> X</pre>
 */
public class FilterChain {

    private FilterChain() {}

    /**
     * Wraps {@code invoker} with {@code filters}, returning a new Invoker whose
     * {@code invoke} method passes the request through every filter before reaching
     * the original invoker.
     */
    public static <T> Invoker<T> buildChain(Invoker<T> invoker, List<Filter> filters) {
        if (filters == null || filters.isEmpty()) return invoker;

        Invoker<T> current = invoker;
        // Build in reverse so the first filter in the list is outermost
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter filter = filters.get(i);
            Invoker<T> next = current;
            current = new Invoker<>() {
                @Override public Class<T> getInterface() { return next.getInterface(); }
                @Override public com.github.tgo18.zrpc.core.common.URL getUrl() { return next.getUrl(); }
                @Override public boolean isAvailable() { return next.isAvailable(); }
                @Override public void destroy() { next.destroy(); }

                @Override
                public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
                    return filter.invoke(next, request);
                }
            };
        }
        return current;
    }
}
