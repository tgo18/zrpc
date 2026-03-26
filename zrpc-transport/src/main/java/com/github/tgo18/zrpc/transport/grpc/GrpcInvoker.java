package com.github.tgo18.zrpc.transport.grpc;

import com.github.tgo18.zrpc.core.codec.Serializer;
import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Consumer-side gRPC Invoker.
 * Sends RPC requests as gRPC unary calls over an existing {@link ManagedChannel}.
 */
public class GrpcInvoker<T> implements Invoker<T> {

    private static final Logger log = LoggerFactory.getLogger(GrpcInvoker.class);

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

    private final Class<T> type;
    private final URL url;
    private final ManagedChannel channel;
    private final Serializer serializer;

    public GrpcInvoker(Class<T> type, URL url, ManagedChannel channel, Serializer serializer) {
        this.type = type;
        this.url = url;
        this.channel = channel;
        this.serializer = serializer;
    }

    @Override public Class<T> getInterface() { return type; }
    @Override public URL getUrl() { return url; }
    @Override public boolean isAvailable() { return !channel.isShutdown() && !channel.isTerminated(); }

    @Override
    public CompletableFuture<RpcResponse> invoke(RpcRequest request) {
        String fullMethodName = type.getName() + "/" + request.getMethodName();

        MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor
                .<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(BYTE_MARSHALLER)
                .setResponseMarshaller(BYTE_MARSHALLER)
                .build();

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();

        try {
            byte[] requestBytes = serializer.serialize(request);
            ClientCall<byte[], byte[]> call = channel.newCall(descriptor, CallOptions.DEFAULT);

            ClientCalls.asyncUnaryCall(call, requestBytes,
                    new io.grpc.stub.StreamObserver<byte[]>() {
                        @Override
                        public void onNext(byte[] responseBytes) {
                            try {
                                future.complete(serializer.deserialize(responseBytes, RpcResponse.class));
                            } catch (Exception e) {
                                future.completeExceptionally(e);
                            }
                        }
                        @Override
                        public void onError(Throwable t) { future.completeExceptionally(t); }
                        @Override
                        public void onCompleted() {}
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void destroy() {
        channel.shutdown();
    }
}
