package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import com.github.tgo18.zrpc.transport.netty.codec.ZRpcDecoder;
import com.github.tgo18.zrpc.transport.netty.codec.ZRpcEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Netty-based RPC server. Handles multiple concurrent client connections,
 * deserializes requests, dispatches them to the appropriate Invoker,
 * and writes responses back.
 */
public class NettyServer {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private static final int BOSS_THREADS   = 1;
    private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private final URL url;
    private final Serializer serializer;
    private final Map<String, Invoker<?>> serviceMap;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyServer(URL url, Serializer serializer) {
        this.url = url;
        this.serializer = serializer;
        this.serviceMap = new ConcurrentHashMap<>();
    }

    /** Register an invoker under its service key */
    public void registerService(String serviceKey, Invoker<?> invoker) {
        serviceMap.put(serviceKey, invoker);
        log.info("Registered service: {}", serviceKey);
    }

    public void start() throws InterruptedException {
        bossGroup   = new NioEventLoopGroup(BOSS_THREADS);
        workerGroup = new NioEventLoopGroup(WORKER_THREADS);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // Heartbeat: close idle connections after 60 s
                                .addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))
                                .addLast(new ZRpcDecoder())
                                .addLast(new ZRpcEncoder())
                                .addLast(new NettyServerHandler(serializer, serviceMap));
                    }
                });

        int port = url.getPort();
        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("zRPC server started on port {}", port);
    }

    public void shutdown() {
        if (serverChannel != null) serverChannel.close();
        if (bossGroup   != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("zRPC server stopped");
    }
}
