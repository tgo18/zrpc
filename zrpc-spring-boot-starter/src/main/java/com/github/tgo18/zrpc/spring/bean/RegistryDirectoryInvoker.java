package com.github.tgo18.zrpc.spring.bean;

import com.github.tgo18.zrpc.core.annotation.ZRpcReference;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.loadbalance.LoadBalance;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.RegistryListener;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import com.github.tgo18.zrpc.spring.config.ZRpcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A cluster-aware Invoker that:
 * <ul>
 *   <li>Subscribes to the registry for live service instance updates</li>
 *   <li>Maintains a pool of per-instance Invokers</li>
 *   <li>Uses load balancing to pick one on each call</li>
 * </ul>
 */
public class RegistryDirectoryInvoker<T> implements Invoker<T>, RegistryListener {

    private static final Logger log = LoggerFactory.getLogger(RegistryDirectoryInvoker.class);

    private final Class<T> type;
    private final ZRpcReference reference;
    private final ZRpcProperties properties;
    private final Registry registry;
    private final Protocol protocol;
    private final LoadBalance loadBalance;

    /** address -> Invoker */
    private final Map<String, Invoker<T>> invokerMap = new ConcurrentHashMap<>();
    private volatile List<Invoker<T>> invokerList = new CopyOnWriteArrayList<>();

    private volatile boolean destroyed = false;

    public RegistryDirectoryInvoker(Class<T> type, ZRpcReference reference,
                                    ZRpcProperties properties, Registry registry,
                                    Protocol protocol, LoadBalance loadBalance) {
        this.type = type;
        this.reference = reference;
        this.properties = properties;
        this.registry = registry;
        this.protocol = protocol;
        this.loadBalance = loadBalance;

        // Subscribe for updates (triggers onChanged immediately with current list)
        registry.subscribe(type.getName(), this);
    }

    @Override
    public void onChanged(String serviceName, List<ServiceInstance> instances) {
        log.info("Service directory updated for {}: {} instances", serviceName, instances.size());
        List<Invoker<T>> fresh = instances.stream()
                .filter(ServiceInstance::isEnabled)
                .map(this::getOrCreateInvoker)
                .collect(Collectors.toList());

        // Destroy removed invokers
        invokerMap.entrySet().removeIf(e -> {
            boolean removed = fresh.stream().noneMatch(i -> i.getUrl().getAddress().equals(e.getKey()));
            if (removed) e.getValue().destroy();
            return removed;
        });

        this.invokerList = fresh;
    }

    @SuppressWarnings("unchecked")
    private Invoker<T> getOrCreateInvoker(ServiceInstance instance) {
        String addr = instance.getAddress();
        return invokerMap.computeIfAbsent(addr, a -> {
            URL url = URL.builder()
                    .protocol(properties.getProtocol().getName())
                    .host(instance.getHost())
                    .port(instance.getPort())
                    .path(type.getName())
                    .parameter(URLKeys.INTERFACE, type.getName())
                    .parameter(URLKeys.VERSION, reference.version())
                    .parameter(URLKeys.GROUP, reference.group())
                    .parameter(URLKeys.TIMEOUT, String.valueOf(
                            reference.timeout() > 0 ? reference.timeout() : properties.getConsumer().getTimeout()))
                    .parameter(URLKeys.SERIALIZATION, properties.getProtocol().getSerialization())
                    .parameter(URLKeys.SIDE, URLKeys.CONSUMER_SIDE)
                    .build();
            return protocol.refer(type, url);
        });
    }

    @Override
    public Class<T> getInterface() { return type; }

    @Override
    public URL getUrl() {
        return URL.builder()
                .protocol(properties.getProtocol().getName())
                .host("0.0.0.0")
                .port(0)
                .path(type.getName())
                .build();
    }

    @Override
    public boolean isAvailable() { return !invokerList.isEmpty(); }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        List<Invoker<T>> available = invokerList.stream()
                .filter(Invoker::isAvailable)
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            return CompletableFuture.failedFuture(new RpcException(
                    RpcException.NO_INVOKER_AVAILABLE,
                    "No available invokers for " + type.getName()));
        }

        Invoker<T> selected = loadBalance.select(available, request);
        return selected.invoke(request);
    }

    @Override
    public void destroy() {
        destroyed = true;
        registry.unsubscribe(type.getName(), this);
        invokerMap.values().forEach(Invoker::destroy);
        invokerMap.clear();
        invokerList = Collections.emptyList();
    }
}
