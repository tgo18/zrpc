package com.github.tgo18.zrpc.transport.grpc;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.protocol.Exporter;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC protocol implementation using the generic/reflection-based gRPC bridge.
 *
 * <p>This allows any zRPC service to be called via a standard gRPC client without
 * generating Protobuf stubs — the request/response are serialized using the
 * configured {@link Serializer} and transported inside gRPC unary calls.
 *
 * <p>Wire path:
 * <pre>
 *   Consumer                           Provider
 *   GrpcInvoker --[gRPC stream]--> GrpcServiceBridge --> Invoker (local impl)
 * </pre>
 *
 * <p>The gRPC service descriptor uses:
 * <ul>
 *   <li>Service name: the fully-qualified Java interface name</li>
 *   <li>Method name: the Java method name</li>
 *   <li>Request/response: raw bytes (serialized RpcRequest / RpcResponse)</li>
 * </ul>
 */
public class GrpcProtocol implements Protocol {

    private static final Logger log = LoggerFactory.getLogger(GrpcProtocol.class);
    public static final int DEFAULT_PORT = 50051;

    private final Map<String, Server> servers    = new ConcurrentHashMap<>();
    private final Map<String, Exporter<?>> exporters = new ConcurrentHashMap<>();

    @Override
    public int getDefaultPort() { return DEFAULT_PORT; }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        Serializer serializer = serializer(url);

        servers.computeIfAbsent(url.getAddress(), addr -> {
            GrpcServiceBridge bridge = new GrpcServiceBridge(invoker, serializer);
            try {
                Server server = ServerBuilder.forPort(url.getPort())
                        .addService(bridge.buildServiceDefinition())
                        .build()
                        .start();
                log.info("gRPC server started on port {}", url.getPort());
                return server;
            } catch (IOException e) {
                throw new RpcException("Failed to start gRPC server on " + addr, e);
            }
        });

        Exporter<T> exporter = new Exporter<>() {
            @Override public Invoker<T> getInvoker() { return invoker; }
            @Override public void unexport() {
                exporters.remove(invoker.getInterface().getName());
            }
        };
        exporters.put(invoker.getInterface().getName(), exporter);
        return exporter;
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        Serializer serializer = serializer(url);
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(url.getHost(), url.getPort())
                .usePlaintext() // Enable TLS in production
                .build();
        return new GrpcInvoker<>(type, url, channel, serializer);
    }

    @Override
    public void destroy() {
        exporters.values().forEach(Exporter::unexport);
        exporters.clear();
        servers.values().forEach(Server::shutdown);
        servers.clear();
    }

    private Serializer serializer(URL url) {
        String name = url.getParameter(URLKeys.SERIALIZATION, "json");
        return ExtensionLoader.getLoader(Serializer.class).getExtension(name);
    }
}
