package com.github.tgo18.zrpc.spring.bean;

import com.github.tgo18.zrpc.core.annotation.ZRpcService;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.protocol.Exporter;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import com.github.tgo18.zrpc.core.proxy.ProxyFactory;
import com.github.tgo18.zrpc.core.registry.Registry;
import com.github.tgo18.zrpc.core.registry.ServiceInstance;
import com.github.tgo18.zrpc.spring.config.ZRpcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scans the Spring application context for {@link ZRpcService}-annotated beans,
 * exports each one via the configured {@link Protocol}, and registers it in the {@link Registry}.
 */
public class ZRpcServiceExporter implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ZRpcServiceExporter.class);

    private final ZRpcProperties properties;
    private ApplicationContext applicationContext;
    private final List<Exporter<?>> exporters = new ArrayList<>();

    public ZRpcServiceExporter(ZRpcProperties properties) {
        this.properties = properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Protocol protocol = ExtensionLoader.getLoader(Protocol.class)
                .getExtension(properties.getProtocol().getName());
        Registry registry = ExtensionLoader.getLoader(Registry.class)
                .getExtension(properties.getRegistry().getType());
        ProxyFactory proxyFactory = ExtensionLoader.getLoader(ProxyFactory.class)
                .getDefaultExtension();

        Map<String, Object> serviceBeans = applicationContext.getBeansWithAnnotation(ZRpcService.class);
        for (Map.Entry<String, Object> entry : serviceBeans.entrySet()) {
            Object bean = entry.getValue();
            ZRpcService annotation = bean.getClass().getAnnotation(ZRpcService.class);
            Class<?> interfaceClass = resolveInterface(bean, annotation);

            URL url = buildServiceUrl(interfaceClass, annotation);
            @SuppressWarnings("unchecked")
            Invoker<Object> invoker = ((ProxyFactory) proxyFactory)
                    .getInvoker(bean, (Class<Object>) interfaceClass, url);

            Exporter<?> exporter = protocol.export(invoker);
            exporters.add(exporter);

            if (annotation.register()) {
                ServiceInstance instance = buildInstance(interfaceClass, annotation, url);
                registry.register(instance);
                log.info("Exported & registered: {}", instance);
            }
        }
    }

    private Class<?> resolveInterface(Object bean, ZRpcService annotation) {
        if (annotation.interfaceClass() != void.class) return annotation.interfaceClass();
        Class<?>[] ifaces = bean.getClass().getInterfaces();
        if (ifaces.length > 0) return ifaces[0];
        throw new IllegalStateException(
                "@ZRpcService on " + bean.getClass() + " has no interface to export");
    }

    private URL buildServiceUrl(Class<?> iface, ZRpcService annotation) {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return URL.builder()
                    .protocol(properties.getProtocol().getName())
                    .host(host)
                    .port(properties.getProtocol().getPort())
                    .path(iface.getName())
                    .parameter(URLKeys.INTERFACE, iface.getName())
                    .parameter(URLKeys.VERSION, annotation.version())
                    .parameter(URLKeys.GROUP, annotation.group())
                    .parameter(URLKeys.SERIALIZATION, properties.getProtocol().getSerialization())
                    .parameter(URLKeys.TIMEOUT, String.valueOf(annotation.timeout()))
                    .parameter(URLKeys.WEIGHT, String.valueOf(annotation.weight()))
                    .parameter(URLKeys.SIDE, URLKeys.PROVIDER_SIDE)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build service URL", e);
        }
    }

    private ServiceInstance buildInstance(Class<?> iface, ZRpcService annotation, URL url) {
        ServiceInstance instance = new ServiceInstance(iface.getName(), url.getHost(), url.getPort());
        instance.setProtocol(properties.getProtocol().getName());
        instance.setVersion(annotation.version());
        instance.setGroup(annotation.group());
        instance.setWeight(annotation.weight());
        instance.putMetadata(URLKeys.SERIALIZATION, properties.getProtocol().getSerialization());
        instance.putMetadata(URLKeys.APPLICATION, properties.getApplication().getName());
        return instance;
    }

    @Override
    public void destroy() {
        exporters.forEach(Exporter::unexport);
    }
}
