package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least-active load balancing.
 *
 * <p>Routes each request to the invoker with the fewest in-flight (active) calls.
 * Among invokers with equal active counts, the selection is weighted-random.
 *
 * <p>Active counts are maintained per (serviceKey, invokerAddress). Callers must
 * call {@link #beginInvoke} before and {@link #endInvoke} after each call to keep
 * the counts accurate. The Spring Boot starter and filter chain do this automatically.
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    /** Shared active-call counter: serviceKey -> addr -> count */
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> ACTIVE_COUNTS =
            new ConcurrentHashMap<>();

    public static void beginInvoke(String serviceKey, String addr) {
        counter(serviceKey, addr).incrementAndGet();
    }

    public static void endInvoke(String serviceKey, String addr) {
        counter(serviceKey, addr).decrementAndGet();
    }

    private static AtomicInteger counter(String serviceKey, String addr) {
        return ACTIVE_COUNTS
                .computeIfAbsent(serviceKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(addr, k -> new AtomicInteger(0));
    }

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request) {
        String serviceKey = request.getServiceKey();
        int leastActive = Integer.MAX_VALUE;
        int totalWeight = 0;
        int[] weights   = new int[invokers.size()];
        List<Integer> leastIndexes = new java.util.ArrayList<>();

        for (int i = 0; i < invokers.size(); i++) {
            Invoker<T> invoker = invokers.get(i);
            String addr = invoker.getUrl().getAddress();
            int active = counter(serviceKey, addr).get();
            int weight = invoker.getUrl().getIntParameter(URLKeys.WEIGHT, 100);
            weights[i] = weight;

            if (active < leastActive) {
                leastActive = active;
                leastIndexes.clear();
                totalWeight = 0;
            }
            if (active == leastActive) {
                leastIndexes.add(i);
                totalWeight += weight;
            }
        }

        if (leastIndexes.size() == 1) {
            return invokers.get(leastIndexes.get(0));
        }

        // Weighted random among least-active invokers
        int offset = ThreadLocalRandom.current().nextInt(totalWeight);
        for (int idx : leastIndexes) {
            offset -= weights[idx];
            if (offset < 0) return invokers.get(idx);
        }
        return invokers.get(leastIndexes.get(0));
    }
}
