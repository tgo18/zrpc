package com.github.tgo18.zrpc.admin.controller;

import com.github.tgo18.zrpc.admin.model.*;
import com.github.tgo18.zrpc.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the zRPC Admin Console.
 *
 * <p>Base path: {@code /api}
 * <p>All responses are JSON.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin  // allow dev-time access from different ports
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---- Dashboard ----

    /** Summary statistics for the dashboard cards. */
    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return adminService.getDashboard();
    }

    // ---- Services ----

    /** List all known services (discovered via MetricsStore + Registry). */
    @GetMapping("/services")
    public List<ServiceInfo> services() {
        return adminService.getAllServices();
    }

    /** Detail of a specific service by name. */
    @GetMapping("/services/{name}")
    public ResponseEntity<ServiceInfo> service(@PathVariable String name) {
        ServiceInfo info = adminService.getService(name);
        return info != null ? ResponseEntity.ok(info) : ResponseEntity.notFound().build();
    }

    // ---- Providers ----

    /** List all provider instances across all services. */
    @GetMapping("/providers")
    public List<ProviderInfo> providers() {
        return adminService.getAllProviders();
    }

    /**
     * Enable or disable a provider instance.
     * @param id  {@code serviceName@host:port}
     * @param body {@code {"enabled": true|false}}
     */
    @PutMapping("/providers/{id}/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        boolean ok = adminService.setProviderEnabled(id, enabled);
        return ResponseEntity.ok(Map.of("success", ok, "id", id, "enabled", enabled));
    }

    /**
     * Update a provider's load balancing weight.
     * @param id  {@code serviceName@host:port}
     * @param body {@code {"weight": 100}}
     */
    @PutMapping("/providers/{id}/weight")
    public ResponseEntity<Map<String, Object>> setWeight(
            @PathVariable String id,
            @RequestBody Map<String, Integer> body) {
        int weight = body.getOrDefault("weight", 100);
        if (weight < 1 || weight > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "weight must be 1–1000"));
        }
        boolean ok = adminService.setProviderWeight(id, weight);
        return ResponseEntity.ok(Map.of("success", ok, "id", id, "weight", weight));
    }

    // ---- Metrics ----

    /** All metrics entries sorted by QPS desc. */
    @GetMapping("/metrics")
    public List<MetricsInfo> metrics() {
        return adminService.getAllMetrics();
    }

    /** Metrics for a specific service (all methods). */
    @GetMapping("/metrics/{service}")
    public List<MetricsInfo> metricsByService(@PathVariable String service) {
        return adminService.getMetricsByService(service);
    }
}
