package com.github.tgo18.zrpc.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.RegistryListener;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Nacos-backed service registry.
 *
 * <p>Nacos instances map to zRPC {@link ServiceInstance} as follows:
 * <ul>
 *   <li>serviceName  → Nacos service name</li>
 *   <li>host:port    → Nacos instance ip:port</li>
 *   <li>metadata     → Nacos instance metadata map (version, group, protocol, etc.)</li>
 *   <li>weight       → Nacos instance weight</li>
 * </ul>
 *
 * <p>Configuration:
 * <pre>
 *   zrpc.registry.type=nacos
 *   zrpc.registry.address=127.0.0.1:8848
 * </pre>
 */
public class NacosRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistry.class);

    private static final String GROUP = "ZRPC";

    private final NamingService namingService;
    private final ConcurrentHashMap<String, List<RegistryListener>> listenerMap = new ConcurrentHashMap<>();

    public NacosRegistry(String serverAddr) {
        try {
            Properties props = new Properties();
            props.setProperty("serverAddr", serverAddr);
            this.namingService = NacosFactory.createNamingService(props);
            log.info("NacosRegistry connected to {}", serverAddr);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to connect to Nacos at " + serverAddr, e);
        }
    }

    @Override
    public void register(ServiceInstance instance) {
        try {
            Instance nacosInstance = toNacosInstance(instance);
            namingService.registerInstance(instance.getServiceName(), GROUP, nacosInstance);
            log.info("Nacos registered: {}", instance);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to register " + instance, e);
        }
    }

    @Override
    public void deregister(ServiceInstance instance) {
        try {
            namingService.deregisterInstance(instance.getServiceName(), GROUP,
                    instance.getHost(), instance.getPort());
            log.info("Nacos deregistered: {}", instance);
        } catch (NacosException e) {
            log.warn("Failed to deregister {}: {}", instance, e.getMessage());
        }
    }

    @Override
    public void subscribe(String serviceName, RegistryListener listener) {
        listenerMap.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>()).add(listener);
        try {
            namingService.subscribe(serviceName, GROUP, event -> {
                if (event instanceof NamingEvent ne) {
                    List<ServiceInstance> instances = ne.getInstances().stream()
                            .map(NacosRegistry::fromNacosInstance)
                            .collect(Collectors.toList());
                    List<RegistryListener> ls = listenerMap.getOrDefault(serviceName, Collections.emptyList());
                    for (RegistryListener l : ls) {
                        try { l.onChanged(serviceName, instances); }
                        catch (Exception ex) { log.warn("Listener error", ex); }
                    }
                }
            });
        } catch (NacosException e) {
            log.error("Failed to subscribe to {}", serviceName, e);
        }
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
        try {
            return namingService.selectInstances(serviceName, GROUP, true)
                    .stream()
                    .map(NacosRegistry::fromNacosInstance)
                    .collect(Collectors.toList());
        } catch (NacosException e) {
            log.error("Failed to get instances for {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable() {
        return "UP".equals(namingService.getServerStatus());
    }

    @Override
    public void destroy() {
        try {
            namingService.shutDown();
        } catch (NacosException ignored) {}
    }

    private static Instance toNacosInstance(ServiceInstance si) {
        Instance i = new Instance();
        i.setIp(si.getHost());
        i.setPort(si.getPort());
        i.setWeight(si.getWeight());
        i.setEnabled(si.isEnabled());
        i.setEphemeral(true);
        Map<String, String> meta = new HashMap<>(si.getMetadata());
        if (si.getVersion() != null) meta.put("version", si.getVersion());
        if (si.getGroup() != null)   meta.put("group",   si.getGroup());
        if (si.getProtocol() != null) meta.put("protocol", si.getProtocol());
        i.setMetadata(meta);
        return i;
    }

    private static ServiceInstance fromNacosInstance(Instance i) {
        ServiceInstance si = new ServiceInstance();
        si.setHost(i.getIp());
        si.setPort(i.getPort());
        si.setWeight((int) i.getWeight());
        si.setEnabled(i.isEnabled());
        si.setMetadata(i.getMetadata());
        si.setVersion(i.getMetadata().get("version"));
        si.setGroup(i.getMetadata().get("group"));
        si.setProtocol(i.getMetadata().get("protocol"));
        return si;
    }
}
