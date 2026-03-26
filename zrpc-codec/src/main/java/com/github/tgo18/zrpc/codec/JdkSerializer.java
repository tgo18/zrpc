package com.github.tgo18.zrpc.codec;

import com.github.tgo18.zrpc.core.codec.SerializationException;
import com.github.tgo18.zrpc.core.codec.Serializer;

import java.io.*;

/**
 * JDK built-in serialization. Only for internal/testing use —
 * prefer JSON or Hessian in production due to security and performance concerns.
 */
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("JDK serialization failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new SerializationException("JDK deserialization failed for " + clazz, e);
        }
    }

    @Override
    public String name() { return "jdk"; }
}
