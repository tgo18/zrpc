package com.github.tgo18.zrpc.core.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPI-based extension loader, similar to Dubbo's ExtensionLoader.
 * Loads extension implementations from META-INF/zrpc/ on the classpath.
 *
 * <p>Usage:
 * <pre>
 *   Serializer serializer = ExtensionLoader.getLoader(Serializer.class).getExtension("json");
 * </pre>
 *
 * <p>Extension file format (META-INF/zrpc/com.github.tgo18.zrpc.core.codec.Serializer):
 * <pre>
 *   json=com.github.tgo18.zrpc.codec.JsonSerializer
 *   protobuf=com.github.tgo18.zrpc.codec.ProtobufSerializer
 * </pre>
 */
public class ExtensionLoader<T> {

    private static final Logger log = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String EXTENSION_DIR = "META-INF/zrpc/";

    /** Cache of loaders per SPI interface */
    private static final ConcurrentHashMap<Class<?>, ExtensionLoader<?>> LOADERS =
            new ConcurrentHashMap<>();

    private final Class<T> type;
    private final String defaultName;

    /** Loaded extension classes: name -> Class */
    private volatile Map<String, Class<? extends T>> extensionClasses;

    /** Singleton instances: name -> instance */
    private final ConcurrentHashMap<String, T> instances = new ConcurrentHashMap<>();

    private ExtensionLoader(Class<T> type) {
        this.type = type;
        SPI spi = type.getAnnotation(SPI.class);
        this.defaultName = spi != null ? spi.value() : "";
    }

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getLoader(Class<T> type) {
        Objects.requireNonNull(type, "Extension type must not be null");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface");
        }
        if (!type.isAnnotationPresent(SPI.class)) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not annotated with @SPI");
        }
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, ExtensionLoader::new);
    }

    /** Get extension by name, returns singleton */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultExtension();
        }
        return instances.computeIfAbsent(name, this::createExtension);
    }

    /** Get the default extension defined by @SPI */
    public T getDefaultExtension() {
        if (defaultName == null || defaultName.isEmpty()) {
            throw new IllegalStateException("No default extension for " + type.getName());
        }
        return getExtension(defaultName);
    }

    /** Check if extension with given name exists */
    public boolean hasExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    /** Get all registered extension names */
    public Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(getExtensionClasses().keySet());
    }

    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Map<String, Class<? extends T>> classes = getExtensionClasses();
        Class<? extends T> clazz = classes.get(name);
        if (clazz == null) {
            throw new IllegalArgumentException("No extension found for '" + name + "' in " + type.getName());
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create extension '" + name + "': " + e.getMessage(), e);
        }
    }

    private Map<String, Class<? extends T>> getExtensionClasses() {
        if (extensionClasses == null) {
            synchronized (this) {
                if (extensionClasses == null) {
                    extensionClasses = loadExtensionClasses();
                }
            }
        }
        return extensionClasses;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Class<? extends T>> loadExtensionClasses() {
        Map<String, Class<? extends T>> map = new LinkedHashMap<>();
        String fileName = EXTENSION_DIR + type.getName();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ExtensionLoader.class.getClassLoader();
            Enumeration<URL> urls = cl.getResources(fileName);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int commentIdx = line.indexOf('#');
                        if (commentIdx >= 0) line = line.substring(0, commentIdx);
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        int eqIdx = line.indexOf('=');
                        if (eqIdx <= 0) continue;
                        String name = line.substring(0, eqIdx).trim();
                        String className = line.substring(eqIdx + 1).trim();
                        try {
                            Class<?> clazz = cl.loadClass(className);
                            if (!type.isAssignableFrom(clazz)) {
                                log.warn("Extension class {} is not a subtype of {}", className, type.getName());
                                continue;
                            }
                            map.put(name, (Class<? extends T>) clazz);
                        } catch (ClassNotFoundException e) {
                            log.warn("Extension class not found: {}", className);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load extensions for {}", type.getName(), e);
        }
        return Collections.unmodifiableMap(map);
    }
}
