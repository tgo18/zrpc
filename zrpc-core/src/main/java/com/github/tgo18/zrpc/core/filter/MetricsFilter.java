package com.github.tgo18.zrpc.core.filter;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Records RPC invocation metrics: latency histogram, success/error counters.
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
        long startNanos = System.nanoTime();
        String service = request.getServiceName();
        String method = request.getMethodName();

        return invoker.invoke(request).whenComplete((response, ex) -> {
            long durationNanos = System.nanoTime() - startNanos;
            boolean success = ex == null && response != null && response.isSuccess();

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
        });
    }
}
