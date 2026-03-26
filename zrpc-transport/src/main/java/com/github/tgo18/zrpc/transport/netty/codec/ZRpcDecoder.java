package com.github.tgo18.zrpc.transport.netty.codec;

import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Decodes the zRPC binary wire format into {@link ZRpcMessage}.
 * Uses Netty's frame length approach — waits until a full frame is available.
 */
public class ZRpcDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Need at least a full header
        if (in.readableBytes() < ZRpcMessage.HEADER_LENGTH) return;

        in.markReaderIndex();

        // Validate magic
        short magic = in.readShort();
        if (magic != ZRpcMessage.MAGIC) {
            in.resetReaderIndex();
            ctx.fireExceptionCaught(new IllegalStateException(
                    "Invalid magic: " + Integer.toHexString(magic & 0xFFFF)));
            ctx.close();
            return;
        }

        byte version = in.readByte();
        if (version != ZRpcMessage.VERSION) {
            ctx.fireExceptionCaught(new IllegalStateException("Unsupported protocol version: " + version));
            ctx.close();
            return;
        }

        byte msgType  = in.readByte();
        byte codec    = in.readByte();
        long reqId    = in.readLong();
        int  bodyLen  = in.readInt();

        if (bodyLen < 0 || bodyLen > ZRpcMessage.MAX_FRAME_LENGTH) {
            ctx.fireExceptionCaught(new IllegalStateException("Invalid body length: " + bodyLen));
            ctx.close();
            return;
        }

        if (in.readableBytes() < bodyLen) {
            // Not enough data yet — wait for more
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[bodyLen];
        in.readBytes(body);

        out.add(new ZRpcMessage(msgType, codec, reqId, body));
    }
}
