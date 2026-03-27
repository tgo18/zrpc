package com.github.tgo18.zrpc.admin.model;

public class MetricsInfo {
    private String service;
    private String method;
    private long totalCalls;
    private long successCalls;
    private long failCalls;
    private double qps;
    private double avgLatencyMs;
    private long maxLatencyMs;
    private double errorRate;

    public MetricsInfo() {}

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }

    public long getSuccessCalls() { return successCalls; }
    public void setSuccessCalls(long successCalls) { this.successCalls = successCalls; }

    public long getFailCalls() { return failCalls; }
    public void setFailCalls(long failCalls) { this.failCalls = failCalls; }

    public double getQps() { return qps; }
    public void setQps(double qps) { this.qps = qps; }

    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public long getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
}
