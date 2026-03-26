package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.protocol.Exporter;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom binary protocol over Netty TCP.
 * One {@link NettyServer} is started per (host, port) combination.
 * Multiple services can share a single server port.
 */
public class NettyProtocol implements Protocol {

    private static final Logger log = LoggerFactory.getLogger(NettyProtocol.class);

    public static final int DEFAULT_PORT = 20880;

    /** Active servers keyed by "host:port" */
    private final Map<String, NettyServer> servers = new ConcurrentHashMap<>();

    /** Active exporters keyed by service key */
    private final Map<String, Exporter<?>> exporters = new ConcurrentHashMap<>();

    @Override
    public int getDefaultPort() { return DEFAULT_PORT; }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        String serverKey = url.getAddress();

        NettyServer server = servers.computeIfAbsent(serverKey, k -> {
            Serializer serializer = serializer(url);
            NettyServer s = new NettyServer(url, serializer);
            try {
                s.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to start NettyServer on " + url.getAddress(), e);
            }
            return s;
        });

        String serviceKey = invoker.getInterface().getName();
        server.registerService(serviceKey, invoker);

        Exporter<T> exporter = new Exporter<>() {
            @Override public Invoker<T> getInvoker() { return invoker; }
            @Override public void unexport() {
                exporters.remove(serviceKey);
                log.info("Unexported service: {}", serviceKey);
            }
        };
        exporters.put(serviceKey, exporter);
        return exporter;
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        Serializer serializer = serializer(url);
        NettyClient client = new NettyClient(url, serializer);
        try {
            client.connect();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to connect to " + url.getAddress(), e);
        }
        return new NettyInvoker<>(type, url, client, serializer);
    }

    @Override
    public void destroy() {
        exporters.values().forEach(Exporter::unexport);
        exporters.clear();
        servers.values().forEach(NettyServer::shutdown);
        servers.clear();
    }

    private Serializer serializer(URL url) {
        String name = url.getParameter(URLKeys.SERIALIZATION, "json");
        return ExtensionLoader.getLoader(Serializer.class).getExtension(name);
    }
}
