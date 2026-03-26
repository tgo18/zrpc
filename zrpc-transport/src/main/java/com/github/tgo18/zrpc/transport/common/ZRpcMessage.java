package com.github.tgo18.zrpc.transport.common;

/**
 * Wire-level message for the custom zRPC binary protocol.
 *
 * Frame layout (fixed 16-byte header):
 * <pre>
 * +---------+---------+----------+---------+----------+----------+
 * | magic   | version | msgType  | codec   | requestId| bodyLen  |
 * | 2 bytes | 1 byte  | 1 byte   | 1 byte  | 8 bytes  | 4 bytes  |
 * +---------+---------+----------+---------+----------+----------+
 * | body (bodyLen bytes) ...                                      |
 * +---------------------------------------------------------------+
 * </pre>
 */
public class ZRpcMessage {

    public static final short MAGIC       = (short) 0xCAFE;
    public static final byte VERSION      = 1;

    // Message types
    public static final byte TYPE_REQUEST  = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_HEARTBEAT_REQ = 3;
    public static final byte TYPE_HEARTBEAT_RESP = 4;

    // Codec types (must match Serializer.name())
    public static final byte CODEC_JSON     = 1;
    public static final byte CODEC_PROTOBUF = 2;
    public static final byte CODEC_HESSIAN  = 3;
    public static final byte CODEC_JDK      = 4;

    // Header size: magic(2) + version(1) + msgType(1) + codec(1) + requestId(8) + bodyLen(4) = 17
    public static final int HEADER_LENGTH = 17;
    public static final int MAX_FRAME_LENGTH = 8 * 1024 * 1024; // 8 MiB

    private byte messageType;
    private byte codec;
    private long requestId;
    private byte[] body;

    public ZRpcMessage() {}

    public ZRpcMessage(byte messageType, byte codec, long requestId, byte[] body) {
        this.messageType = messageType;
        this.codec = codec;
        this.requestId = requestId;
        this.body = body;
    }

    public boolean isRequest() { return messageType == TYPE_REQUEST; }
    public boolean isResponse() { return messageType == TYPE_RESPONSE; }
    public boolean isHeartbeat() {
        return messageType == TYPE_HEARTBEAT_REQ || messageType == TYPE_HEARTBEAT_RESP;
    }

    public byte getMessageType() { return messageType; }
    public void setMessageType(byte messageType) { this.messageType = messageType; }

    public byte getCodec() { return codec; }
    public void setCodec(byte codec) { this.codec = codec; }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body; }
}
