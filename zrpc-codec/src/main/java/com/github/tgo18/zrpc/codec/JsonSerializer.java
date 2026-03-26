package com.github.tgo18.zrpc.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tgo18.zrpc.core.codec.SerializationException;
import com.github.tgo18.zrpc.core.codec.Serializer;

/**
 * JSON serializer backed by Jackson ObjectMapper.
 * Fast and human-readable; works well for debugging and heterogeneous environments.
 */
public class JsonSerializer implements Serializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new SerializationException("JSON serialization failed for " + obj.getClass(), e);
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException {
        try {
            return MAPPER.readValue(bytes, clazz);
        } catch (Exception e) {
            throw new SerializationException("JSON deserialization failed for " + clazz, e);
        }
    }

    @Override
    public String name() { return "json"; }
}
