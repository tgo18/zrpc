package com.github.tgo18.zrpc.transport.http2;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumer-side HTTP/2 Invoker.
 * Opens a new HTTP/2 stream per RPC call, sends the serialized request as a DATA frame,
 * and returns a future that completes when the response DATA frame arrives.
 */
public class Http2Invoker<T> implements Invoker<T> {

    private static final Logger log = LoggerFactory.getLogger(Http2Invoker.class);

    /** HTTP/2 stream IDs must be odd positive integers, starting at 1 */
    private final AtomicInteger streamIdGen = new AtomicInteger(1);

    private final Class<T> type;
    private final URL url;
    private final Channel channel;
    private final Serializer serializer;
    private final ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pending;

    public Http2Invoker(Class<T> type, URL url, Channel channel,
                        Serializer serializer,
                        ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pending) {
        this.type = type;
        this.url = url;
        this.channel = channel;
        this.serializer = serializer;
        this.pending = pending;
    }

    @Override public Class<T> getInterface() { return type; }
    @Override public URL getUrl() { return url; }
    @Override public boolean isAvailable() { return channel.isActive(); }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        int streamId = streamIdGen.getAndAdd(2); // client streams are odd
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pending.put(streamId, future);

        try {
            byte[] body = serializer.serialize(request);

            Http2Headers headers = new DefaultHttp2Headers()
                    .method("POST")
                    .path("/" + request.getServiceName() + "/" + request.getMethodName())
                    .scheme("https")
                    .add("content-type", "application/zrpc");

            Http2FrameStream stream = channel.pipeline()
                    .get(Http2FrameCodec.class)
                    .newStream();

            Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers).stream(stream);
            Http2DataFrame dataFrame = new DefaultHttp2DataFrame(
                    Unpooled.wrappedBuffer(body), true).stream(stream);

            channel.write(headersFrame);
            channel.writeAndFlush(dataFrame).addListener(f -> {
                if (!f.isSuccess()) {
                    pending.remove(streamId);
                    future.completeExceptionally(f.cause());
                }
            });
        } catch (Exception e) {
            pending.remove(streamId);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void destroy() {
        channel.close();
        pending.forEach((id, f) ->
                f.completeExceptionally(new java.io.IOException("Invoker destroyed")));
        pending.clear();
    }
}
