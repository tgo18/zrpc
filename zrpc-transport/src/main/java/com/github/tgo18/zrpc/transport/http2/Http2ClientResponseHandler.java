package com.github.tgo18.zrpc.transport.http2;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/2 client-side handler.
 * Receives DATA frames carrying serialized {@link RpcResponse} bodies and
 * completes the pending future for the corresponding stream.
 */
public class Http2ClientResponseHandler extends Http2FrameAdapter {

    private static final Logger log = LoggerFactory.getLogger(Http2ClientResponseHandler.class);

    /** streamId -> pending future */
    private final ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pending;
    private final Serializer serializer;

    public Http2ClientResponseHandler(
            ConcurrentHashMap<Integer, CompletableFuture<RpcResponse>> pending,
            Serializer serializer) {
        this.pending = pending;
        this.serializer = serializer;
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                          int padding, boolean endOfStream) throws Http2Exception {
        int processed = data.readableBytes() + padding;
        if (!endOfStream) return processed;

        byte[] bytes = new byte[data.readableBytes()];
        data.readBytes(bytes);

        CompletableFuture<RpcResponse> future = pending.remove(streamId);
        if (future == null) {
            log.warn("No pending future for HTTP/2 stream {}", streamId);
            return processed;
        }
        try {
            RpcResponse response = serializer.deserialize(bytes, RpcResponse.class);
            future.complete(response);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return processed;
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        CompletableFuture<RpcResponse> future = pending.remove(streamId);
        if (future != null) {
            future.completeExceptionally(
                    new java.io.IOException("HTTP/2 stream reset, errorCode=" + errorCode));
        }
    }
}
