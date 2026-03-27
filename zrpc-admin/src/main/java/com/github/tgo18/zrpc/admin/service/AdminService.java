package com.github.tgo18.zrpc.admin.service;

import com.github.tgo18.zrpc.admin.model.*;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.metrics.MetricEntry;
import com.github.tgo18.zrpc.core.metrics.MetricsStore;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import com.github.tgo18.zrpc.spring.config.ZRpcProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core admin business logic: aggregates data from the Registry and MetricsStore.
 */
@Service
public class AdminService {

    private final ZRpcProperties properties;
    private final Registry registry;

    public AdminService(ZRpcProperties properties) {
        this.properties = properties;
        this.registry = ExtensionLoader.getLoader(Registry.class)
                .getExtension(properties.getRegistry().getType());
    }

    // ---- Dashboard ----

    public DashboardStats getDashboard() {
        List<ServiceInfo> services = getAllServices();
        List<ProviderInfo> providers = getAllProviders();
        Collection<MetricEntry> metrics = MetricsStore.entries();

        long totalCalls = metrics.stream().mapToLong(MetricEntry::getTotalCalls).sum();
        double totalQps = metrics.stream().mapToDouble(MetricEntry::getQps).sum();
        double avgLatency = metrics.stream()
                .filter(e -> e.getTotalCalls() > 0)
                .mapToDouble(MetricEntry::getAvgLatency)
                .average().orElse(0.0);
        long totalFail = metrics.stream().mapToLong(MetricEntry::getFailCalls).sum();
        double errorRate = totalCalls == 0 ? 0.0 : (double) totalFail / totalCalls * 100;

        DashboardStats stats = new DashboardStats();
        stats.setTotalServices(services.size());
        stats.setTotalProviders(providers.size());
        stats.setTotalCalls(totalCalls);
        stats.setTotalQps(round(totalQps));
        stats.setAvgLatencyMs(round(avgLatency));
        stats.setErrorRate(round(errorRate));
        stats.setRegistryType(properties.getRegistry().getType());
        stats.setRegistryAvailable(registry.isAvailable());
        return stats;
    }

    // ---- Services ----

    public List<ServiceInfo> getAllServices() {
        // Aggregate providers grouped by service name
        Map<String, List<ServiceInstance>> byService = new HashMap<>();
        // Scan MetricsStore for known service names
        for (MetricEntry e : MetricsStore.entries()) {
            byService.computeIfAbsent(e.getService(), k -> new ArrayList<>());
        }
        // Also include services that have registered instances
        // (admin may be embedded in provider, so we can query registry)
        for (Map.Entry<String, List<ServiceInstance>> entry : byService.entrySet()) {
            List<ServiceInstance> instances = registry.getInstances(entry.getKey());
            entry.getValue().addAll(instances);
        }

        List<ServiceInfo> result = new ArrayList<>();
        for (Map.Entry<String, List<ServiceInstance>> entry : byService.entrySet()) {
            String svcName = entry.getKey();
            ServiceInfo info = new ServiceInfo();
            info.setName(svcName);
            info.setProviders(entry.getValue().stream()
                    .map(this::toProviderInfo).collect(Collectors.toList()));

            // Aggregate metrics across all methods for this service
            List<MetricEntry> svcMetrics = MetricsStore.entries().stream()
                    .filter(e -> e.getService().equals(svcName))
                    .collect(Collectors.toList());

            long total = svcMetrics.stream().mapToLong(MetricEntry::getTotalCalls).sum();
            double qps  = svcMetrics.stream().mapToDouble(MetricEntry::getQps).sum();
            double avg  = svcMetrics.stream().filter(e -> e.getTotalCalls() > 0)
                    .mapToDouble(MetricEntry::getAvgLatency).average().orElse(0);
            long fail   = svcMetrics.stream().mapToLong(MetricEntry::getFailCalls).sum();

            info.setTotalCalls(total);
            info.setQps(round(qps));
            info.setAvgLatencyMs(round(avg));
            info.setErrorRate(round(total == 0 ? 0 : (double) fail / total * 100));
            result.add(info);
        }
        return result;
    }

    public ServiceInfo getService(String name) {
        return getAllServices().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst().orElse(null);
    }

    // ---- Providers ----

    public List<ProviderInfo> getAllProviders() {
        // Pull all known service names from MetricsStore and scan registry
        Set<String> serviceNames = MetricsStore.entries().stream()
                .map(MetricEntry::getService).collect(Collectors.toSet());

        return serviceNames.stream()
                .flatMap(svc -> registry.getInstances(svc).stream())
                .map(this::toProviderInfo)
                .collect(Collectors.toList());
    }

    public boolean setProviderEnabled(String serviceId, boolean enabled) {
        // serviceId = "serviceName@host:port"
        String[] parts = serviceId.split("@");
        if (parts.length != 2) return false;
        String serviceName = parts[0];
        String[] hostPort  = parts[1].split(":");
        if (hostPort.length != 2) return false;

        List<ServiceInstance> instances = registry.getInstances(serviceName);
        for (ServiceInstance si : instances) {
            if (si.getHost().equals(hostPort[0]) && si.getPort() == Integer.parseInt(hostPort[1])) {
                si.setEnabled(enabled);
                registry.deregister(si);
                registry.register(si);
                return true;
            }
        }
        return false;
    }

    public boolean setProviderWeight(String serviceId, int weight) {
        String[] parts = serviceId.split("@");
        if (parts.length != 2) return false;
        String serviceName = parts[0];
        String[] hostPort  = parts[1].split(":");
        if (hostPort.length != 2) return false;

        List<ServiceInstance> instances = registry.getInstances(serviceName);
        for (ServiceInstance si : instances) {
            if (si.getHost().equals(hostPort[0]) && si.getPort() == Integer.parseInt(hostPort[1])) {
                si.setWeight(weight);
                registry.deregister(si);
                registry.register(si);
                return true;
            }
        }
        return false;
    }

    // ---- Metrics ----

    public List<MetricsInfo> getAllMetrics() {
        return MetricsStore.entries().stream()
                .map(this::toMetricsInfo)
                .sorted(Comparator.comparingDouble(MetricsInfo::getQps).reversed())
                .collect(Collectors.toList());
    }

    public List<MetricsInfo> getMetricsByService(String service) {
        return MetricsStore.entries().stream()
                .filter(e -> e.getService().equals(service))
                .map(this::toMetricsInfo)
                .collect(Collectors.toList());
    }

    // ---- Helpers ----

    private ProviderInfo toProviderInfo(ServiceInstance si) {
        ProviderInfo p = new ProviderInfo();
        p.setId(si.getServiceName() + "@" + si.getAddress());
        p.setHost(si.getHost());
        p.setPort(si.getPort());
        p.setServiceName(si.getServiceName());
        p.setVersion(si.getVersion());
        p.setGroup(si.getGroup());
        p.setProtocol(si.getProtocol());
        p.setWeight(si.getWeight());
        p.setEnabled(si.isEnabled());
        p.setApplication(si.getMetadata("application"));
        return p;
    }

    private MetricsInfo toMetricsInfo(MetricEntry e) {
        MetricsInfo m = new MetricsInfo();
        m.setService(e.getService());
        m.setMethod(e.getMethod());
        m.setTotalCalls(e.getTotalCalls());
        m.setSuccessCalls(e.getSuccessCalls());
        m.setFailCalls(e.getFailCalls());
        m.setQps(round(e.getQps()));
        m.setAvgLatencyMs(round(e.getAvgLatency()));
        m.setMaxLatencyMs(e.getMaxLatency());
        m.setErrorRate(round(e.getErrorRate()));
        return m;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
