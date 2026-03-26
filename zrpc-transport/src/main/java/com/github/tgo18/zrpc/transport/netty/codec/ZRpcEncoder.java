package com.github.tgo18.zrpc.transport.netty.codec;

import com.github.tgo18.zrpc.transport.common.ZRpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes {@link ZRpcMessage} into the zRPC binary wire format.
 *
 * Header (17 bytes):
 * [0-1]  magic   = 0xCAFE
 * [2]    version = 1
 * [3]    msgType
 * [4]    codec
 * [5-12] requestId (long)
 * [13-16] bodyLen (int)
 * [17+]  body bytes
 */
public class ZRpcEncoder extends MessageToByteEncoder<ZRpcMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ZRpcMessage msg, ByteBuf out) {
        byte[] body = msg.getBody() != null ? msg.getBody() : new byte[0];

        out.writeShort(ZRpcMessage.MAGIC);
        out.writeByte(ZRpcMessage.VERSION);
        out.writeByte(msg.getMessageType());
        out.writeByte(msg.getCodec());
        out.writeLong(msg.getRequestId());
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}
