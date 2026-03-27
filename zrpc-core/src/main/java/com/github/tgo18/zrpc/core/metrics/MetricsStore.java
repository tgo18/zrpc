package com.github.tgo18.zrpc.core.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global in-process metrics registry.
 *
 * <p>The {@code MetricsFilter} calls {@link #record} after each invocation.
 * The zRPC Admin console reads from {@link #entries()} via REST API.
 *
 * <p>Keys are formatted as {@code "serviceName#methodName"}.
 */
public final class MetricsStore {

    private MetricsStore() {}

    private static final ConcurrentHashMap<String, MetricEntry> STORE = new ConcurrentHashMap<>();

    /**
     * Record one invocation result.
     *
     * @param service    service interface name
     * @param method     method name
     * @param latencyMs  call duration in milliseconds
     * @param success    whether the call succeeded
     */
    public static void record(String service, String method, long latencyMs, boolean success) {
        String key = service + "#" + method;
        STORE.computeIfAbsent(key, k -> new MetricEntry(service, method))
             .record(latencyMs, success);
    }

    /** All metric entries (live view, not a copy). */
    public static Collection<MetricEntry> entries() {
        return Collections.unmodifiableCollection(STORE.values());
    }

    /** Metric entry for a specific service+method, or null if never called. */
    public static MetricEntry get(String service, String method) {
        return STORE.get(service + "#" + method);
    }

    /** Remove all metrics (e.g., for testing). */
    public static void clear() {
        STORE.clear();
    }
}
