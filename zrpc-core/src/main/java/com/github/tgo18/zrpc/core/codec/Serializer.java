package com.github.tgo18.zrpc.core.codec;

import com.github.tgo18.zrpc.core.extension.SPI;

/**
 * Serialization SPI for encoding/decoding RPC payloads.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@code json} — Jackson JSON</li>
 *   <li>{@code protobuf} — Google Protocol Buffers</li>
 *   <li>{@code hessian} — Hessian binary</li>
 *   <li>{@code jdk} — Java built-in serialization (not recommended for production)</li>
 * </ul>
 */
@SPI("json")
public interface Serializer {

    /** Serialize an object to bytes */
    byte[] serialize(Object obj) throws SerializationException;

    /** Deserialize bytes back to the specified type */
    <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException;

    /** Returns the serializer name/identifier */
    String name();
}
