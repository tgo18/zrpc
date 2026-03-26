package com.github.tgo18.zrpc.transport.http2;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.common.URLKeys;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.extension.ExtensionLoader;
import com.github.tgo18.zrpc.core.protocol.Exporter;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.core.protocol.Protocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/2 protocol implementation using Netty's native HTTP/2 codec.
 *
 * <ul>
 *   <li>Server side: accepts HTTP/2 streams, maps each stream to an RPC invocation</li>
 *   <li>Client side: opens HTTP/2 streams for each request, with multiplexing</li>
 *   <li>TLS: enabled by default (self-signed in dev); configure with real certs in production</li>
 * </ul>
 *
 * <p>Request framing: each HTTP/2 DATA frame carries a serialized {@link RpcRequest}.
 * The response DATA frame carries a serialized {@link RpcResponse}.
 * Service name and method are encoded as HTTP/2 pseudo-headers (:path, :method POST).
 */
public class Http2Protocol implements Protocol {

    private static final Logger log = LoggerFactory.getLogger(Http2Protocol.class);
    public static final int DEFAULT_PORT = 20881;

    private static final EventLoopGroup SERVER_BOSS   = new NioEventLoopGroup(1);
    private static final EventLoopGroup SERVER_WORKER = new NioEventLoopGroup();
    private static final EventLoopGroup CLIENT_GROUP  = new NioEventLoopGroup();

    private final Map<String, Channel> serverChannels = new ConcurrentHashMap<>();
    private final Map<String, Exporter<?>> exporters  = new ConcurrentHashMap<>();

    @Override
    public int getDefaultPort() { return DEFAULT_PORT; }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        Serializer serializer = serializer(url);

        serverChannels.computeIfAbsent(url.getAddress(), addr -> {
            try {
                SelfSignedCertificate cert = new SelfSignedCertificate();
                SslContext sslCtx = SslContextBuilder
                        .forServer(cert.certificate(), cert.privateKey())
                        .build();

                Http2FrameLogger frameLogger = new Http2FrameLogger(
                        io.netty.handler.logging.LogLevel.DEBUG, Http2Protocol.class);

                ServerBootstrap b = new ServerBootstrap()
                        .group(SERVER_BOSS, SERVER_WORKER)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline()
                                        .addLast(sslCtx.newHandler(ch.alloc()))
                                        .addLast(buildHttp2ConnectionHandler(serializer, invoker));
                            }
                        });

                Channel ch = b.bind(url.getPort()).sync().channel();
                log.info("HTTP/2 server started on port {}", url.getPort());
                return ch;
            } catch (Exception e) {
                throw new RpcException("Failed to start HTTP/2 server on " + addr, e);
            }
        });

        Exporter<T> exporter = new Exporter<>() {
            @Override public Invoker<T> getInvoker() { return invoker; }
            @Override public void unexport() { exporters.remove(invoker.getInterface().getName()); }
        };
        exporters.put(invoker.getInterface().getName(), exporter);
        return exporter;
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) {
        Serializer serializer = serializer(url);
        try {
            SslContext sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient().build();
            Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(
                    new ChannelInitializer<Channel>() {
                        @Override protected void initChannel(Channel ch) {}
                    });

            ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();

            Bootstrap b = new Bootstrap()
                    .group(CLIENT_GROUP)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(sslCtx.newHandler(ch.alloc(),
                                            url.getHost(), url.getPort()))
                                    .addLast(frameCodec)
                                    .addLast(multiplexHandler)
                                    .addLast(new Http2ClientResponseHandler(pending, serializer));
                        }
                    });

            Channel channel = b.connect(url.getHost(), url.getPort()).sync().channel();
            return new Http2Invoker<>(type, url, channel, serializer, pending);
        } catch (Exception e) {
            throw new RpcException("Failed to connect HTTP/2 client to " + url.getAddress(), e);
        }
    }

    @Override
    public void destroy() {
        exporters.values().forEach(Exporter::unexport);
        serverChannels.values().forEach(Channel::close);
        serverChannels.clear();
    }

    private ChannelHandler buildHttp2ConnectionHandler(Serializer serializer, Invoker<?> invoker) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        return new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(new Http2ServerFrameListener(connection, serializer, invoker))
                .connection(connection)
                .build();
    }

    private Serializer serializer(URL url) {
        String name = url.getParameter(URLKeys.SERIALIZATION, "json");
        return ExtensionLoader.getLoader(Serializer.class).getExtension(name);
    }
}
