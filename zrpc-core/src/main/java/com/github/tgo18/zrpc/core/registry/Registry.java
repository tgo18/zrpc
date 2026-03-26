package com.github.tgo18.zrpc.core.registry;

import com.github.tgo18.zrpc.core.extension.SPI;

import java.util.List;

/**
 * Service registry SPI.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code memory} — in-process registry (for testing/embedded use)</li>
 *   <li>{@code zookeeper} — Apache ZooKeeper-backed registry</li>
 *   <li>{@code nacos} — Alibaba Nacos-backed registry</li>
 * </ul>
 */
@SPI("memory")
public interface Registry {

    /** Register a service instance */
    void register(ServiceInstance instance);

    /** Deregister a service instance */
    void deregister(ServiceInstance instance);

    /**
     * Subscribe to changes in the list of instances for a service.
     * The listener is called immediately with the current list and on every change.
     */
    void subscribe(String serviceName, RegistryListener listener);

    /** Unsubscribe from service change notifications */
    void unsubscribe(String serviceName, RegistryListener listener);

    /** Get current instances for a service */
    List<ServiceInstance> getInstances(String serviceName);

    /** Check whether this registry is connected */
    boolean isAvailable();

    /** Release all resources */
    void destroy();
}
