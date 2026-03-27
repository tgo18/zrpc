package com.github.tgo18.zrpc.admin.model;

public class DashboardStats {
    private int totalServices;
    private int totalProviders;
    private long totalCalls;
    private double totalQps;
    private double avgLatencyMs;
    private double errorRate;
    private String registryType;
    private boolean registryAvailable;

    public int getTotalServices() { return totalServices; }
    public void setTotalServices(int totalServices) { this.totalServices = totalServices; }

    public int getTotalProviders() { return totalProviders; }
    public void setTotalProviders(int totalProviders) { this.totalProviders = totalProviders; }

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }

    public double getTotalQps() { return totalQps; }
    public void setTotalQps(double totalQps) { this.totalQps = totalQps; }

    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }

    public String getRegistryType() { return registryType; }
    public void setRegistryType(String registryType) { this.registryType = registryType; }

    public boolean isRegistryAvailable() { return registryAvailable; }
    public void setRegistryAvailable(boolean registryAvailable) { this.registryAvailable = registryAvailable; }
}
