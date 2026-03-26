package com.github.tgo18.zrpc.spring.bean;

import com.github.tgo18.zrpc.core.annotation.ZRpcReference;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.loadbalance.LoadBalance;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import com.github.tgo18.zrpc.core.proxy.ProxyFactory;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import com.github.tgo18.zrpc.spring.config.ZRpcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BeanPostProcessor} that injects zRPC consumer proxies into fields
 * annotated with {@link ZRpcReference}.
 *
 * <p>For each annotated field, the injector:
 * <ol>
 *   <li>Looks up current service instances from the registry</li>
 *   <li>Creates Invokers via {@link Protocol#refer}</li>
 *   <li>Wraps them in a cluster invoker with load balancing</li>
 *   <li>Generates a dynamic proxy and injects it into the field</li>
 * </ol>
 */
public class ZRpcReferenceInjector implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZRpcReferenceInjector.class);

    private final ZRpcProperties properties;

    /** Cache of proxies: interfaceName -> proxy */
    private final Map<String, Object> proxyCache = new ConcurrentHashMap<>();

    public ZRpcReferenceInjector(ZRpcProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                ZRpcReference reference = field.getAnnotation(ZRpcReference.class);
                if (reference != null) {
                    Object proxy = getOrCreateProxy(field.getType(), reference);
                    field.setAccessible(true);
                    try {
                        field.set(bean, proxy);
                        log.info("Injected @ZRpcReference into {}.{}", clazz.getSimpleName(), field.getName());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to inject @ZRpcReference into " + field, e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return bean;
    }

    @SuppressWarnings("unchecked")
    private <T> Object getOrCreateProxy(Class<T> type, ZRpcReference reference) {
        String cacheKey = type.getName() + ":" + reference.version() + ":" + reference.group();
        return proxyCache.computeIfAbsent(cacheKey, k -> createProxy(type, reference));
    }

    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> type, ZRpcReference reference) {
        Registry registry = ExtensionLoader.getLoader(Registry.class)
                .getExtension(properties.getRegistry().getType());
        Protocol protocol = ExtensionLoader.getLoader(Protocol.class)
                .getExtension(properties.getProtocol().getName());
        LoadBalance loadBalance = ExtensionLoader.getLoader(LoadBalance.class)
                .getExtension(reference.loadbalance().isEmpty()
                        ? properties.getConsumer().getLoadbalance()
                        : reference.loadbalance());
        ProxyFactory proxyFactory = ExtensionLoader.getLoader(ProxyFactory.class).getDefaultExtension();

        // Build a cluster invoker that fetches fresh invokers from the registry on each call
        Invoker<T> clusterInvoker = new RegistryDirectoryInvoker<>(
                type, reference, properties, registry, protocol, loadBalance);

        return proxyFactory.getProxy(clusterInvoker, type);
    }
}
