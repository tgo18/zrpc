package com.github.tgo18.zrpc.registry.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.RegistryListener;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ZooKeeper-backed service registry using Apache Curator.
 *
 * <p>ZNode layout:
 * <pre>
 * /zrpc
 *   /{serviceName}
 *     /providers
 *       /{host:port}  (ephemeral, data = JSON serialized ServiceInstance)
 * </pre>
 *
 * <p>Configuration via constructor or Spring Boot auto-configuration:
 * <pre>
 *   zrpc.registry.type=zookeeper
 *   zrpc.registry.address=127.0.0.1:2181
 * </pre>
 */
public class ZookeeperRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private static final String ROOT_PATH      = "/zrpc";
    private static final String PROVIDERS_NODE = "providers";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CuratorFramework client;
    private final ConcurrentHashMap<String, PathChildrenCache> caches    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<RegistryListener>> listenerMap = new ConcurrentHashMap<>();

    public ZookeeperRegistry(String connectString) {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(30_000)
                .connectionTimeoutMs(10_000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("zrpc")
                .build();
        this.client.start();
        try {
            this.client.blockUntilConnected(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ZooKeeper connection interrupted", e);
        }
        log.info("ZookeeperRegistry connected to {}", connectString);
    }

    @Override
    public void register(ServiceInstance instance) {
        String path = providerPath(instance.getServiceName(), instance.getId());
        try {
            byte[] data = MAPPER.writeValueAsBytes(instance);
            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, data);
            } else {
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath(path, data);
            }
            log.info("ZK registered: {}", path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register " + instance, e);
        }
    }

    @Override
    public void deregister(ServiceInstance instance) {
        String path = providerPath(instance.getServiceName(), instance.getId());
        try {
            client.delete().quietly().forPath(path);
            log.info("ZK deregistered: {}", path);
        } catch (Exception e) {
            log.warn("Failed to deregister {}: {}", path, e.getMessage());
        }
    }

    @Override
    public void subscribe(String serviceName, RegistryListener listener) {
        listenerMap.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>()).add(listener);
        caches.computeIfAbsent(serviceName, sn -> startWatcher(sn, listener));
        // Immediate snapshot
        listener.onChanged(serviceName, getInstances(serviceName));
    }

    @Override
    public void unsubscribe(String serviceName, RegistryListener listener) {
        List<RegistryListener> ls = listenerMap.get(serviceName);
        if (ls != null) ls.remove(listener);
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        String basePath = providersPath(serviceName);
        try {
            if (client.checkExists().forPath(basePath) == null) return Collections.emptyList();
            List<String> children = client.getChildren().forPath(basePath);
            List<ServiceInstance> instances = new ArrayList<>();
            for (String child : children) {
                byte[] data = client.getData().forPath(ZKPaths.makePath(basePath, child));
                if (data != null && data.length > 0) {
                    instances.add(MAPPER.readValue(data, ServiceInstance.class));
                }
            }
            return instances;
        } catch (Exception e) {
            log.error("Failed to get instances for {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable() {
        return client.getZookeeperClient().isConnected();
    }

    @Override
    public void destroy() {
        caches.values().forEach(c -> {
            try { c.close(); } catch (Exception ignored) {}
        });
        client.close();
    }

    private PathChildrenCache startWatcher(String serviceName, RegistryListener firstListener) {
        PathChildrenCache cache = new PathChildrenCache(client, providersPath(serviceName), true);
        try {
            cache.getListenable().addListener((c, event) -> {
                List<ServiceInstance> instances = getInstances(serviceName);
                List<RegistryListener> ls = listenerMap.getOrDefault(serviceName, Collections.emptyList());
                for (RegistryListener l : ls) {
                    try { l.onChanged(serviceName, instances); }
                    catch (Exception e) { log.warn("Listener error", e); }
                }
            });
            cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        } catch (Exception e) {
            log.error("Failed to start ZK watcher for {}", serviceName, e);
        }
        return cache;
    }

    private String providersPath(String serviceName) {
        return "/" + serviceName + "/" + PROVIDERS_NODE;
    }

    private String providerPath(String serviceName, String id) {
        return providersPath(serviceName) + "/" + id;
    }
}
