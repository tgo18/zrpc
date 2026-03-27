package com.github.tgo18.zrpc.admin.model;

import java.util.List;

public class ServiceInfo {
    private String name;
    private List<ProviderInfo> providers;
    private long totalCalls;
    private double qps;
    private double avgLatencyMs;
    private double errorRate;

    public ServiceInfo() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ProviderInfo> getProviders() { return providers; }
    public void setProviders(List<ProviderInfo> providers) { this.providers = providers; }

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }

    public double getQps() { return qps; }
    public void setQps(double qps) { this.qps = qps; }

    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
}
