package com.github.tgo18.zrpc.loadbalance;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consistent hash load balancing with virtual nodes.
 *
 * <p>Uses MD5-based hashing with 160 virtual nodes per invoker (40 nodes × 4 hashes).
 * The hash key is composed of the method name and the first argument's string representation.
 * This ensures the same caller always routes to the same provider (sticky sessions),
 * which is useful for caching and stateful services.
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    private static final int VIRTUAL_NODES = 160;

    private final ConcurrentHashMap<String, ConsistentHashSelector<?>> selectors =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, RpcRequest request) {
        String serviceKey = request.getServiceKey();
        int identityHash = System.identityHashCode(invokers);
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(serviceKey);
        if (selector == null || selector.identityHash != identityHash) {
            selectors.put(serviceKey, new ConsistentHashSelector<>(invokers, identityHash));
            selector = (ConsistentHashSelector<T>) selectors.get(serviceKey);
        }
        return selector.select(buildHashKey(request));
    }

    private String buildHashKey(RpcRequest request) {
        StringBuilder sb = new StringBuilder(request.getMethodName());
        Object[] args = request.getArguments();
        if (args != null && args.length > 0 && args[0] != null) {
            sb.append(args[0]);
        }
        return sb.toString();
    }

    private static class ConsistentHashSelector<T> {
        private final TreeMap<Long, Invoker<T>> ring = new TreeMap<>();
        final int identityHash;

        ConsistentHashSelector(List<Invoker<T>> invokers, int identityHash) {
            this.identityHash = identityHash;
            for (Invoker<T> invoker : invokers) {
                String addr = invoker.getUrl().getAddress();
                // 40 nodes × 4 hash positions per MD5 = 160 virtual nodes
                for (int i = 0; i < VIRTUAL_NODES / 4; i++) {
                    byte[] digest = md5(addr + "-node-" + i);
                    for (int j = 0; j < 4; j++) {
                        long hash = ((long)(digest[3 + j*4] & 0xFF) << 24)
                                  | ((long)(digest[2 + j*4] & 0xFF) << 16)
                                  | ((long)(digest[1 + j*4] & 0xFF) << 8)
                                  | ((long)(digest[j*4]     & 0xFF));
                        ring.put(hash, invoker);
                    }
                }
            }
        }

        Invoker<T> select(String key) {
            byte[] digest = md5(key);
            long hash = ((long)(digest[3] & 0xFF) << 24)
                      | ((long)(digest[2] & 0xFF) << 16)
                      | ((long)(digest[1] & 0xFF) << 8)
                      | ((long)(digest[0] & 0xFF));
            Map.Entry<Long, Invoker<T>> entry = ring.ceilingEntry(hash);
            if (entry == null) entry = ring.firstEntry();
            return entry.getValue();
        }

        private static byte[] md5(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                return md.digest(key.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e); // MD5 always available
            }
        }
    }
}
