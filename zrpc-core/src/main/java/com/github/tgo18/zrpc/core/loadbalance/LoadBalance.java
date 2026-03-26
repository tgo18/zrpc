package com.github.tgo18.zrpc.core.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.extension.SPI;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.List;

/**
 * Load balancing SPI — selects one Invoker from a list for a given request.
 *
 * <p>Built-in strategies:
 * <ul>
 *   <li>{@code roundrobin} — smooth weighted round-robin</li>
 *   <li>{@code random} — weighted random</li>
 *   <li>{@code consistenthash} — consistent hash (sticky sessions)</li>
 *   <li>{@code leastactive} — routes to invoker with fewest in-flight calls</li>
 * </ul>
 */
@SPI("roundrobin")
public interface LoadBalance {

    /**
     * Select one invoker from the list.
     *
     * @param invokers available invokers (never null, never empty)
     * @param request  the incoming request (used for hash-based strategies)
     * @return the selected invoker
     */
    <T> Invoker<T> select(List<Invoker<T>> invokers, RpcRequest request);
}
