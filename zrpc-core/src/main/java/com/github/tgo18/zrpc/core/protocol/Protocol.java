package com.github.tgo18.zrpc.core.protocol;

import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.extension.SPI;

/**
 * Protocol SPI — handles exporting (server side) and referencing (client side) of services.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@code zrpc} — binary protocol over Netty TCP</li>
 *   <li>{@code grpc} — gRPC over HTTP/2</li>
 *   <li>{@code http2} — custom framing over HTTP/2</li>
 * </ul>
 */
@SPI("zrpc")
public interface Protocol {

    /** The default port for this protocol */
    int getDefaultPort();

    /**
     * Export a service so it can be called remotely.
     *
     * @param invoker the local invoker wrapping the service implementation
     * @return an Exporter that can be used to un-export the service
     */
    <T> Exporter<T> export(Invoker<T> invoker);

    /**
     * Create a remote invoker (stub) that routes calls to the server at the given URL.
     *
     * @param type the service interface
     * @param url  server address
     * @return an Invoker connected to the remote server
     */
    <T> Invoker<T> refer(Class<T> type, URL url);

    /** Release all resources held by this protocol */
    void destroy();
}
