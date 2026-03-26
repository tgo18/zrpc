package com.github.tgo18.zrpc.transport.grpc;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.grpc.*;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Bridges a zRPC {@link Invoker} to a gRPC {@link ServerServiceDefinition}.
 *
 * <p>Registers one gRPC unary method per public interface method,
 * each accepting and returning raw bytes ({@code MethodDescriptor<byte[], byte[]>}).
 * Incoming bytes are deserialized to {@link RpcRequest}; response bytes carry
 * the serialized {@link RpcResponse}.
 */
public class GrpcServiceBridge {

    private static final Logger log = LoggerFactory.getLogger(GrpcServiceBridge.class);

    private static final MethodDescriptor.Marshaller<byte[]> BYTE_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public java.io.InputStream stream(byte[] value) {
                    return new java.io.ByteArrayInputStream(value);
                }
                @Override
                public byte[] parse(java.io.InputStream stream) {
                    try { return stream.readAllBytes(); }
                    catch (java.io.IOException e) { throw new RuntimeException(e); }
                }
            };

    private final Invoker<?> invoker;
    private final Serializer serializer;

    public GrpcServiceBridge(Invoker<?> invoker, Serializer serializer) {
        this.invoker = invoker;
        this.serializer = serializer;
    }

    public ServerServiceDefinition buildServiceDefinition() {
        Class<?> iface = invoker.getInterface();
        String serviceName = iface.getName();
        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(serviceName);

        for (Method method : iface.getMethods()) {
            String fullMethodName = serviceName + "/" + method.getName();

            MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor
                    .<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(fullMethodName)
                    .setRequestMarshaller(BYTE_MARSHALLER)
                    .setResponseMarshaller(BYTE_MARSHALLER)
                    .build();

            ServerCallHandler<byte[], byte[]> handler = ServerCalls.asyncUnaryCall(
                    (requestBytes, responseObserver) ->
                            handleCall(requestBytes, responseObserver, method));

            builder.addMethod(descriptor, handler);
        }
        return builder.build();
    }

    private void handleCall(byte[] requestBytes, StreamObserver<byte[]> responseObserver, Method method) {
        try {
            RpcRequest request = serializer.deserialize(requestBytes, RpcRequest.class);
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());

            invoker.invoke(request).whenComplete((response, ex) -> {
                if (ex != null) {
                    response = RpcResponse.error(request.getRequestId(), ex);
                }
                try {
                    responseObserver.onNext(serializer.serialize(response));
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription(e.getMessage()).asRuntimeException());
                }
            });
        } catch (Exception e) {
            log.error("gRPC bridge error", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
