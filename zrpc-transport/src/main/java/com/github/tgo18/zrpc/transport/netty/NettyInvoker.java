package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.protocol.Invoker;

import java.util.concurrent.CompletableFuture;

/**
 * Consumer-side Invoker that sends requests over a {@link NettyClient}.
 * Deserializes the raw-bytes result returned by {@link NettyClientHandler} back
 * into a proper {@link RpcResponse}.
 *
 * @param <T> service interface type
 */
public class NettyInvoker<T> implements Invoker<T> {

    private final Class<T> type;
    private final URL url;
    private final NettyClient client;
    private final Serializer serializer;

    public NettyInvoker(Class<T> type, URL url, NettyClient client, Serializer serializer) {
        this.type = type;
        this.url = url;
        this.client = client;
        this.serializer = serializer;
    }

    @Override
    public Class<T> getInterface() { return type; }

    @Override
    public URL getUrl() { return url; }

    @Override
    public boolean isAvailable() { return client.isActive(); }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        if (!client.isActive()) {
            return CompletableFuture.failedFuture(new RpcException(
                    RpcException.NETWORK_EXCEPTION, "Client not connected to " + url.getAddress()));
        }

        return client.send(request).thenApply(rawResponse -> {
            // NettyClientHandler stores raw bytes in result; decode here
            if (rawResponse.getResult() instanceof byte[] bytes) {
                return serializer.deserialize(bytes, RpcResponse.class);
            }
            return rawResponse;
        });
    }

    @Override
    public void destroy() {
        client.close();
    }
}
