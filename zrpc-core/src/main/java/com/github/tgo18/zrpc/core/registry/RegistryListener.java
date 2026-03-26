package com.github.tgo18.zrpc.core.registry;

import java.util.List;

/**
 * Callback for service instance changes in the registry.
 */
@FunctionalInterface
public interface RegistryListener {

    /**
     * Called when the list of instances for a service changes.
     *
     * @param serviceName the service that changed
     * @param instances   current full list of available instances
     */
    void onChanged(String serviceName, List<ServiceInstance> instances);
}
