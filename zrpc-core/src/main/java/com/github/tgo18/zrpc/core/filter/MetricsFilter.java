package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.metrics.MetricsStore;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Records RPC invocation metrics:
 * <ul>
 *   <li>Micrometer: latency histogram, error counter (for Prometheus/Grafana)</li>
 *   <li>MetricsStore: in-process store consumed by the zRPC Admin console</li>
 * </ul>
 */
public class MetricsFilter implements Filter {

    private final MeterRegistry registry;

    public MetricsFilter() {
        this(new SimpleMeterRegistry());
    }

    public MetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CompletableFuture<RpcResponse> invoke(Invoker<?> invoker, RpcRequest request) {
        long startMs    = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        String service  = request.getServiceName();
        String method   = request.getMethodName();

        return invoker.invoke(request).whenComplete((response, ex) -> {
            long durationMs    = System.currentTimeMillis() - startMs;
            long durationNanos = System.nanoTime() - startNanos;
            boolean success    = ex == null && response != null && response.isSuccess();

            // 1. Micrometer (Prometheus / Grafana integration)
            Timer.builder("zrpc.invocation.duration")
                    .tag("service", service)
                    .tag("method", method)
                    .tag("success", String.valueOf(success))
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            if (!success) {
                registry.counter("zrpc.invocation.errors",
                        "service", service, "method", method).increment();
            }

            // 2. In-process MetricsStore (consumed by zrpc-admin console)
            MetricsStore.record(service, method, durationMs, success);
        });
    }
}
