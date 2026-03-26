package com.github.tgo18.zrpc.codec;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.github.tgo18.zrpc.core.codec.SerializationException;
import com.github.tgo18.zrpc.core.codec.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Hessian binary serializer — compact binary format with good cross-language support.
 * Suitable for high-throughput scenarios where JSON overhead is a concern.
 */
public class HessianSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            HessianOutput ho = new HessianOutput(bos);
            ho.writeObject(obj);
            ho.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("Hessian serialization failed", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws SerializationException {
        try {
            HessianInput hi = new HessianInput(new ByteArrayInputStream(bytes));
            return (T) hi.readObject(clazz);
        } catch (Exception e) {
            throw new SerializationException("Hessian deserialization failed for " + clazz, e);
        }
    }

    @Override
    public String name() { return "hessian"; }
}
