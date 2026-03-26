package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smooth Weighted Round-Robin load balancing.
 *
 * <p>Each invoker has a current weight that increases by its configured weight on
 * every round. The invoker with the highest current weight is selected and its
 * current weight is reduced by the total weight. This produces a smooth distribution
 * even when weights differ significantly, matching Nginx's algorithm.
 *
 * <p>Example with weights [5, 1, 1]:
 * Round 1: A(5) → select A, current = [-2, 1, 1]
 * Round 2: A(3) → select A, current = [-4, 2, 2]
 * Round 3: B(3) → wait... produces A,A,B,A,C,A,A,A,B pattern → smooth.
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    /** serviceKey -> invoker address -> WeightedRoundRobin state */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WeightedRoundRobin>> state =
            new ConcurrentHashMap<>();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request) {
        String serviceKey = request.getServiceKey();
        ConcurrentHashMap<String, WeightedRoundRobin> map =
                state.computeIfAbsent(serviceKey, k -> new ConcurrentHashMap<>());

        int totalWeight = 0;
        Invoker<T> selected = null;
        int maxCurrent = Integer.MIN_VALUE;

        for (Invoker<T> invoker : invokers) {
            String addr = invoker.getUrl().getAddress();
            int weight = invoker.getUrl().getIntParameter(URLKeys.WEIGHT, 100);

            WeightedRoundRobin wrr = map.computeIfAbsent(addr, k -> new WeightedRoundRobin(weight));
            wrr.setWeight(weight);

            int current = wrr.increaseCurrent();
            totalWeight += weight;

            if (current > maxCurrent) {
                maxCurrent = current;
                selected = invoker;
            }
        }

        if (selected != null) {
            String addr = selected.getUrl().getAddress();
            map.get(addr).sel(totalWeight);
        }
        return selected != null ? selected : invokers.get(0);
    }

    private static class WeightedRoundRobin {
        private volatile int weight;
        private final AtomicInteger current = new AtomicInteger(0);

        WeightedRoundRobin(int weight) { this.weight = weight; }

        void setWeight(int weight) { this.weight = weight; }

        int increaseCurrent() { return current.addAndGet(weight); }

        void sel(int total) { current.addAndGet(-total); }
    }
}
