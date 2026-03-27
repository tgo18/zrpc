package com.github.tgo18.zrpc.core.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-(service, method) call statistics.
 * All updates are lock-free via atomic / LongAdder operations.
 */
public class MetricEntry {

    private final String service;
    private final String method;

    private final LongAdder totalCalls   = new LongAdder();
    private final LongAdder successCalls = new LongAdder();
    private final LongAdder failCalls    = new LongAdder();
    private final LongAdder totalLatency = new LongAdder(); // milliseconds
    private final AtomicLong maxLatency  = new AtomicLong(0);

    // Simple sliding-window QPS: calls in the last second
    private volatile long lastWindowMs  = System.currentTimeMillis();
    private final LongAdder windowCalls = new LongAdder();
    private volatile double qps         = 0.0;

    public MetricEntry(String service, String method) {
        this.service = service;
        this.method  = method;
    }

    public void record(long latencyMs, boolean success) {
        totalCalls.increment();
        totalLatency.add(latencyMs);

        // Update max latency
        long cur;
        do { cur = maxLatency.get(); } while (latencyMs > cur && !maxLatency.compareAndSet(cur, latencyMs));

        if (success) successCalls.increment();
        else         failCalls.increment();

        windowCalls.increment();
        slideWindow();
    }

    private void slideWindow() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWindowMs;
        if (elapsed >= 1000) {
            long calls = windowCalls.sumThenReset();
            qps = calls * 1000.0 / elapsed;
            lastWindowMs = now;
        }
    }

    // ---- Snapshot getters (called by admin API) ----

    public String getService()   { return service; }
    public String getMethod()    { return method; }
    public long getTotalCalls()  { return totalCalls.sum(); }
    public long getSuccessCalls(){ return successCalls.sum(); }
    public long getFailCalls()   { return failCalls.sum(); }
    public long getTotalLatency(){ return totalLatency.sum(); }
    public long getMaxLatency()  { return maxLatency.get(); }
    public double getQps()       { slideWindow(); return qps; }

    public double getAvgLatency() {
        long total = totalCalls.sum();
        return total == 0 ? 0.0 : (double) totalLatency.sum() / total;
    }

    public double getErrorRate() {
        long total = totalCalls.sum();
        return total == 0 ? 0.0 : (double) failCalls.sum() / total * 100.0;
    }
}
