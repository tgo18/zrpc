package com.github.tgo18.zrpc.registry.memory;

import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.RegistryListener;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process service registry backed by a ConcurrentHashMap.
 * Useful for testing, local development, and single-JVM deployments.
 * Not suitable for distributed deployments — instances are not shared across processes.
 */
public class InMemoryRegistry implements Registry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRegistry.class);

    /** serviceName -> list of registered instances */
    private final ConcurrentHashMap<String, List<ServiceInstance>> store = new ConcurrentHashMap<>();

    /** serviceName -> listeners */
    private final ConcurrentHashMap<String, List<RegistryListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void register(ServiceInstance instance) {
        store.computeIfAbsent(instance.getServiceName(),
                k -> new CopyOnWriteArrayList<>()).add(instance);
        log.info("Registered: {}", instance);
        notifyListeners(instance.getServiceName());
    }

    @Override
    public void deregister(ServiceInstance instance) {
        List<ServiceInstance> list = store.get(instance.getServiceName());
        if (list != null) {
            list.remove(instance);
            log.info("Deregistered: {}", instance);
            notifyListeners(instance.getServiceName());
        }
    }

    @Override
    public void subscribe(String serviceName, RegistryListener listener) {
        listeners.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>()).add(listener);
        // Immediately notify with current state
        List<ServiceInstance> current = getInstances(serviceName);
        listener.onChanged(serviceName, current);
    }

    @Override
    public void unsubscribe(String serviceName, RegistryListener listener) {
        List<RegistryListener> ls = listeners.get(serviceName);
        if (ls != null) ls.remove(listener);
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        List<ServiceInstance> list = store.get(serviceName);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public void destroy() {
        store.clear();
        listeners.clear();
    }

    private void notifyListeners(String serviceName) {
        List<RegistryListener> ls = listeners.get(serviceName);
        if (ls == null || ls.isEmpty()) return;
        List<ServiceInstance> current = getInstances(serviceName);
        for (RegistryListener l : ls) {
            try {
                l.onChanged(serviceName, current);
            } catch (Exception e) {
                log.warn("Listener error for {}", serviceName, e);
            }
        }
    }
}
