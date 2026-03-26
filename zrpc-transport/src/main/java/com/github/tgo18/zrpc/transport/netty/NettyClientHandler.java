package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side Netty handler: receives response messages and completes
 * the corresponding pending future.
 */
public class NettyClientHandler extends SimpleChannelInboundHandler<ZRpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(NettyClientHandler.class);

    private final ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests;
    private final NettyClient client;

    public NettyClientHandler(
            ConcurrentHashMap<Long, CompletableFuture<RpcResponse>> pendingRequests,
            NettyClient client) {
        this.pendingRequests = pendingRequests;
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ZRpcMessage msg) {
        if (msg.isHeartbeat()) return; // discard heartbeat responses

        if (!msg.isResponse()) {
            log.warn("Client received unexpected message type: {}", msg.getMessageType());
            return;
        }

        long requestId = msg.getRequestId();
        CompletableFuture<RpcResponse> future = pendingRequests.remove(requestId);
        if (future == null) {
            log.warn("No pending request for response id={}", requestId);
            return;
        }

        // The response body is raw bytes; caller's invoker will deserialize into RpcResponse
        // We store the raw message body in a minimal wrapper for the invoker to decode
        RpcResponse fakeResponse = new RpcResponse(requestId);
        fakeResponse.setResult(msg.getBody()); // raw bytes; NettyInvoker will decode
        future.complete(fakeResponse);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent ide && ide.state() == IdleState.WRITER_IDLE) {
            // Send heartbeat
            ZRpcMessage ping = new ZRpcMessage(
                    ZRpcMessage.TYPE_HEARTBEAT_REQ, ZRpcMessage.CODEC_JSON, 0, new byte[0]);
            ctx.writeAndFlush(ping);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client channel error: {}", cause.getMessage());
        // Fail all pending requests
        pendingRequests.forEach((id, f) -> f.completeExceptionally(cause));
        pendingRequests.clear();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        pendingRequests.forEach((id, f) ->
                f.completeExceptionally(new java.io.IOException("Connection lost")));
        pendingRequests.clear();
    }
}
