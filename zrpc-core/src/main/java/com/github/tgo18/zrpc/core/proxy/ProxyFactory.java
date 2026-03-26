package com.github.tgo18.zrpc.core.proxy;

import com.github.tgo18.zrpc.core.extension.SPI;
import com.github.tgo18.zrpc.core.protocol.Invoker;

/**
 * Proxy factory SPI — creates dynamic proxies for service consumers,
 * and wraps service implementation instances into Invokers for providers.
 */
@SPI("bytebuddy")
public interface ProxyFactory {

    /**
     * Create a consumer-side proxy that implements {@code type} and
     * routes all method calls through {@code invoker}.
     */
    <T> T getProxy(Invoker<T> invoker, Class<T> type);

    /**
     * Wrap a service implementation instance into an Invoker.
     */
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, com.github.tgo18.zrpc.core.common.URL url);
}
