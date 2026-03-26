package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import com.github.tgo18.zrpc.transport.netty.codec.ZRpcDecoder;
import com.github.tgo18.zrpc.transport.netty.codec.ZRpcEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Netty-based RPC client. Maintains a persistent connection to one server,
 * multiplexes concurrent requests over it, and returns futures to callers.
 *
 * <p>Uses a {@link ConcurrentHashMap} as an in-flight registry: each request ID
 * maps to its pending CompletableFuture, which is completed when the server responds.
 */
public class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private static final EventLoopGroup SHARED_GROUP =
            new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

    private final URL serverUrl;
    private final Serializer serializer;
    private final byte codecByte;

    /** In-flight requests: requestId -> pending future */
    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests =
            new ConcurrentHashMap<>();

    private volatile Channel channel;
    private volatile boolean closed = false;

    public NettyClient(URL serverUrl, Serializer serializer) {
        this.serverUrl = serverUrl;
        this.serializer = serializer;
        this.codecByte = codecByName(serializer.name());
    }

    public synchronized void connect() throws InterruptedException {
        if (channel != null && channel.isActive()) return;

        Bootstrap bootstrap = new Bootstrap()
                .group(SHARED_GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS))
                                .addLast(new ZRpcDecoder())
                                .addLast(new ZRpcEncoder())
                                .addLast(new NettyClientHandler(pendingRequests, NettyClient.this));
                    }
                });

        channel = bootstrap.connect(serverUrl.getHost(), serverUrl.getPort()).sync().channel();
        log.info("Connected to {}", serverUrl.getAddress());

        // Auto-reconnect on disconnect
        channel.closeFuture().addListener(f -> {
            if (!closed) scheduleReconnect();
        });
    }

    public CompletableFuture<RpcResponse> send(RpcRequest request) {
        if (channel == null || !channel.isActive()) {
            return CompletableFuture.failedFuture(
                    new RpcException(RpcException.NETWORK_EXCEPTION, "Not connected to " + serverUrl.getAddress()));
        }

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), future);

        try {
            byte[] body = serializer.serialize(request);
            ZRpcMessage msg = new ZRpcMessage(ZRpcMessage.TYPE_REQUEST, codecByte, request.getRequestId(), body);
            channel.writeAndFlush(msg).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    pendingRequests.remove(request.getRequestId());
                    future.completeExceptionally(new RpcException(RpcException.NETWORK_EXCEPTION,
                            "Failed to send request", writeFuture.cause()));
                }
            });
        } catch (Exception e) {
            pendingRequests.remove(request.getRequestId());
            future.completeExceptionally(e);
        }

        return future;
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public void close() {
        closed = true;
        if (channel != null) channel.close();
        // Fail all pending requests
        pendingRequests.forEach((id, f) ->
                f.completeExceptionally(new RpcException(RpcException.NETWORK_EXCEPTION, "Client closed")));
        pendingRequests.clear();
    }

    private void scheduleReconnect() {
        log.warn("Disconnected from {}. Reconnecting in 3s...", serverUrl.getAddress());
        SHARED_GROUP.schedule(() -> {
            try {
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 3, TimeUnit.SECONDS);
    }

    private static byte codecByName(String name) {
        return switch (name) {
            case "json"     -> ZRpcMessage.CODEC_JSON;
            case "protobuf" -> ZRpcMessage.CODEC_PROTOBUF;
            case "hessian"  -> ZRpcMessage.CODEC_HESSIAN;
            default         -> ZRpcMessage.CODEC_JDK;
        };
    }
}
