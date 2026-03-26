package com.github.tgo18.zrpc.codec;

import com.github.tgo18.zrpc.core.codec.SerializationException;
import com.github.tgo18.zrpc.core.codec.Serializer;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import java.lang.reflect.Method;

/**
 * Protobuf serializer for {@link Message} types.
 * Falls back to JSON for non-Protobuf objects (using JsonFormat).
 *
 * <p>For maximum performance on the gRPC transport, use this serializer
 * with the gRPC protocol implementation which also handles Protobuf framing natively.
 */
public class ProtobufSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        if (obj instanceof Message msg) {
            return msg.toByteArray();
        }
        throw new SerializationException(
                "ProtobufSerializer only supports com.google.protobuf.Message, got: " + obj.getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException {
        if (!Message.class.isAssignableFrom(clazz)) {
            throw new SerializationException(
                    "ProtobufSerializer only supports com.google.protobuf.Message, got: " + clazz);
        }
        try {
            Method parseFrom = clazz.getMethod("parseFrom", byte[].class);
            return (T) parseFrom.invoke(null, bytes);
        } catch (Exception e) {
            throw new SerializationException("Protobuf deserialization failed for " + clazz, e);
        }
    }

    @Override
    public String name() { return "protobuf"; }
}
