package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted random load balancing.
 * Probability of selecting an invoker is proportional to its weight.
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request) {
        int totalWeight = 0;
        boolean sameWeight = true;
        int[] weights = new int[invokers.size()];

        for (int i = 0; i < invokers.size(); i++) {
            int w = invokers.get(i).getUrl().getIntParameter(URLKeys.WEIGHT, 100);
            weights[i] = w;
            totalWeight += w;
            if (sameWeight && i > 0 && w != weights[i - 1]) sameWeight = false;
        }

        if (!sameWeight && totalWeight > 0) {
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < invokers.size(); i++) {
                offset -= weights[i];
                if (offset < 0) return invokers.get(i);
            }
        }

        return invokers.get(ThreadLocalRandom.current().nextInt(invokers.size()));
    }
}
