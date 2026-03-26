package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalanceTest {

    private static Invoker<Object> invoker(String host, int port, int weight) {
        URL url = URL.builder().host(host).port(port).parameter("weight", String.valueOf(weight)).build();
        return new Invoker<>() {
            @Override public Class<Object> getInterface() { return Object.class; }
            @Override public URL getUrl() { return url; }
            @Override public boolean isAvailable() { return true; }
            @Override public void destroy() {}
            @Override public CompletableFuture<com.github.tgo18.zrpc.core.common.RpcResponse> invoke(RpcRequest r) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static RpcRequest req() {
        RpcRequest r = new RpcRequest();
        r.setServiceName("TestService");
        r.setMethodName("test");
        r.setArguments(new Object[]{"key1"});
        return r;
    }

    @Test
    void roundRobinDistributesEvenly() {
        List<Invoker<Object>> invokers = List.of(
                invoker("a", 1, 100), invoker("b", 2, 100), invoker("c", 3, 100));
        RoundRobinLoadBalance lb = new RoundRobinLoadBalance();

        Map<String, AtomicInteger> counts = invokers.stream()
                .collect(Collectors.toMap(i -> i.getUrl().getHost(), i -> new AtomicInteger()));

        for (int i = 0; i < 300; i++) {
            Invoker<Object> selected = lb.select(invokers, req());
            counts.get(selected.getUrl().getHost()).incrementAndGet();
        }

        // Each should receive ~100 out of 300
        counts.values().forEach(c -> assertTrue(c.get() > 80 && c.get() < 120,
                "Count out of range: " + c.get()));
    }

    @Test
    void randomSelectsFromList() {
        List<Invoker<Object>> invokers = List.of(
                invoker("a", 1, 100), invoker("b", 2, 200), invoker("c", 3, 100));
        RandomLoadBalance lb = new RandomLoadBalance();

        // Just check it always returns one of the invokers
        for (int i = 0; i < 50; i++) {
            Invoker<Object> selected = lb.select(invokers, req());
            assertTrue(invokers.contains(selected));
        }
    }

    @Test
    void consistentHashSticksToSameInvoker() {
        List<Invoker<Object>> invokers = List.of(
                invoker("a", 1, 100), invoker("b", 2, 100), invoker("c", 3, 100));
        ConsistentHashLoadBalance lb = new ConsistentHashLoadBalance();

        RpcRequest request = req();
        Invoker<Object> first = lb.select(invokers, request);

        // Same request key should always map to same invoker
        for (int i = 0; i < 20; i++) {
            assertSame(first, lb.select(invokers, request));
        }
    }

    @Test
    void singleInvokerAlwaysSelected() {
        List<Invoker<Object>> single = List.of(invoker("only", 9999, 100));
        for (var lb : List.of(new RoundRobinLoadBalance(), new RandomLoadBalance(),
                               new ConsistentHashLoadBalance(), new LeastActiveLoadBalance())) {
            assertSame(single.get(0), lb.select(single, req()));
        }
    }
}
