package com.github.tgo18.zrpc.transport.http2;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/2 server-side frame listener.
 * Receives DATA frames, deserializes RpcRequest, invokes the service,
 * and writes the RpcResponse back as a DATA frame on the same stream.
 */
public class Http2ServerFrameListener extends Http2FrameAdapter {

    private static final Logger log = LoggerFactory.getLogger(Http2ServerFrameListener.class);

    private final Http2Connection connection;
    private final Serializer serializer;
    private final Invoker<?> invoker;

    public Http2ServerFrameListener(Http2Connection connection, Serializer serializer, Invoker<?> invoker) {
        this.connection = connection;
        this.serializer = serializer;
        this.invoker = invoker;
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                          int padding, boolean endOfStream) throws Http2Exception {
        int processed = data.readableBytes() + padding;

        if (!endOfStream) return processed; // Wait for full frame

        byte[] bytes = new byte[data.readableBytes()];
        data.readBytes(bytes);

        try {
            RpcRequest request = serializer.deserialize(bytes, RpcRequest.class);
            invoker.invoke(request).whenComplete((response, ex) -> {
                if (ex != null) {
                    response = RpcResponse.error(request.getRequestId(), ex);
                }
                writeResponse(ctx, streamId, response);
            });
        } catch (Exception e) {
            log.error("Error processing HTTP/2 request on stream {}", streamId, e);
            RpcResponse err = RpcResponse.error(-1, e);
            writeResponse(ctx, streamId, err);
        }

        return processed;
    }

    private void writeResponse(ChannelHandlerContext ctx, int streamId, RpcResponse response) {
        try {
            byte[] body = serializer.serialize(response);

            Http2Headers headers = new DefaultHttp2Headers()
                    .status("200")
                    .add("content-type", "application/zrpc");

            Http2ConnectionEncoder encoder = ((Http2ConnectionHandler)
                    ctx.pipeline().get(Http2ConnectionHandler.class)).encoder();

            encoder.writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
            encoder.writeData(ctx, streamId, Unpooled.wrappedBuffer(body), 0, true, ctx.newPromise());
            ctx.flush();
        } catch (Exception e) {
            log.error("Failed to write HTTP/2 response on stream {}", streamId, e);
        }
    }
}
