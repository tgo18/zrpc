package com.github.tgo18.zrpc.transport.netty;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Server-side Netty handler: decodes {@link ZRpcMessage} into {@link RpcRequest},
 * dispatches to the registered Invoker, and writes the {@link RpcResponse} back.
 */
@ChannelHandler.Sharable
public class NettyServerHandler extends SimpleChannelInboundHandler<ZRpcMessage> {

    private static final Logger log = LoggerFactory.getLogger(NettyServerHandler.class);

    // Business thread pool — keeps I/O threads free
    private static final Executor BUSINESS_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor(); // Java 21 virtual threads

    private final Serializer serializer;
    private final Map<String, Invoker<?>> serviceMap;

    public NettyServerHandler(Serializer serializer, Map<String, Invoker<?>> serviceMap) {
        this.serializer = serializer;
        this.serviceMap = serviceMap;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ZRpcMessage msg) {
        if (msg.isHeartbeat()) {
            // Echo heartbeat response
            ZRpcMessage pong = new ZRpcMessage(
                    ZRpcMessage.TYPE_HEARTBEAT_RESP, msg.getCodec(), msg.getRequestId(), new byte[0]);
            ctx.writeAndFlush(pong);
            return;
        }

        if (!msg.isRequest()) {
            log.warn("Server received unexpected message type: {}", msg.getMessageType());
            return;
        }

        BUSINESS_EXECUTOR.execute(() -> handleRequest(ctx, msg));
    }

    private void handleRequest(ChannelHandlerContext ctx, ZRpcMessage msg) {
        RpcRequest request = null;
        try {
            request = serializer.deserialize(msg.getBody(), RpcRequest.class);
            Invoker<?> invoker = serviceMap.get(request.getServiceKey());

            if (invoker == null) {
                RpcResponse resp = RpcResponse.error(request.getRequestId(),
                        RpcResponse.Status.SERVICE_NOT_FOUND,
                        new IllegalStateException("Service not found: " + request.getServiceKey()));
                writeResponse(ctx, msg, resp);
                return;
            }

            final RpcRequest req = request;
            invoker.invoke(request).whenComplete((resp, ex) -> {
                if (ex != null) {
                    writeResponse(ctx, msg, RpcResponse.error(req.getRequestId(), ex));
                } else {
                    writeResponse(ctx, msg, resp);
                }
            });
        } catch (Exception e) {
            long reqId = request != null ? request.getRequestId() : -1;
            log.error("Error handling request {}", reqId, e);
            writeResponse(ctx, msg, RpcResponse.error(reqId, e));
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, ZRpcMessage requestMsg, RpcResponse response) {
        try {
            byte[] body = serializer.serialize(response);
            ZRpcMessage respMsg = new ZRpcMessage(
                    ZRpcMessage.TYPE_RESPONSE, requestMsg.getCodec(), response.getRequestId(), body);
            ctx.writeAndFlush(respMsg);
        } catch (Exception e) {
            log.error("Failed to serialize response for request {}", response.getRequestId(), e);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent ide && ide.state() == IdleState.READER_IDLE) {
            log.info("Closing idle connection: {}", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in server channel {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}
