package com.github.tgo18.zrpc.core;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.filter.Filter;
import com.github.tgo18.zrpc.core.filter.FilterChain;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class FilterChainTest {

    /** Tracks invocation order */
    private final List<String> order = new ArrayList<>();

    private Invoker<Object> makeInvoker(String name) {
        return new Invoker<>() {
            @Override public Class<Object> getInterface() { return Object.class; }
            @Override public URL getUrl() { return URL.builder().host("localhost").port(1).build(); }
            @Override public boolean isAvailable() { return true; }
            @Override public void destroy() {}
            @Override
            public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
                order.add(name);
                return CompletableFuture.completedFuture(
                        RpcResponse.success(request.getRequestId(), "result-from-" + name));
            }
        };
    }

    private Filter makeFilter(String name) {
        return (invoker, request) -> {
            order.add("before-" + name);
            return invoker.invoke(request).thenApply(r -> {
                order.add("after-" + name);
                return r;
            });
        };
    }

    @Test
    void filtersExecuteInOrder() throws Exception {
        Invoker<Object> root = makeInvoker("root");
        List<Filter> filters = List.of(makeFilter("A"), makeFilter("B"), makeFilter("C"));
        Invoker<Object> chain = FilterChain.buildChain(root, filters);

        RpcRequest req = new RpcRequest();
        req.setServiceName("TestService");
        req.setMethodName("test");

        RpcResponse response = chain.invoke(req).get();

        assertEquals(List.of("before-A", "before-B", "before-C", "root",
                "after-C", "after-B", "after-A"), order);
        assertTrue(response.isSuccess());
        assertEquals("result-from-root", response.getResult());
    }

    @Test
    void emptyFilterListPassesThrough() throws Exception {
        Invoker<Object> root = makeInvoker("root");
        Invoker<Object> chain = FilterChain.buildChain(root, List.of());
        assertSame(root, chain);
    }
}
