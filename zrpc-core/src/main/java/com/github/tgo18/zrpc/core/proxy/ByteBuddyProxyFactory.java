package com.github.tgo18.zrpc.core.proxy;

import com.github.tgo18.zrpc.core.common.RpcRequest;
import com.github.tgo18.zrpc.core.common.RpcResponse;
import com.github.tgo18.zrpc.core.common.URL;
import com.github.tgo18.zrpc.core.exception.RpcException;
import com.github.tgo18.zrpc.core.protocol.Invoker;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * ByteBuddy-based proxy factory. Generates bytecode proxies at runtime,
 * which is faster than JDK dynamic proxies for repeated invocations.
 */
public class ByteBuddyProxyFactory implements ProxyFactory {

    private static final ByteBuddy BYTE_BUDDY = new ByteBuddy();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<T> type) {
        InvocationHandler handler = new RpcInvocationHandler<>(invoker);
        try {
            Class<? extends T> proxyClass = BYTE_BUDDY
                    .subclass(type)
                    .method(ElementMatchers.isPublic())
                    .intercept(InvocationHandlerAdapter.of(handler))
                    .make()
                    .load(type.getClassLoader())
                    .getLoaded();
            return proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RpcException("Failed to create proxy for " + type.getName(), e);
        }
    }

    @Override
    public <T> Invoker<T> getInvoker(T impl, Class<T> type, URL url) {
        return new AbstractProxyInvoker<>(impl, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, Class<?>[] parameterTypes, Object[] args)
                    throws Exception {
                Method method = proxy.getClass().getMethod(methodName, parameterTypes);
                return method.invoke(proxy, args);
            }
        };
    }

    // --- Inner classes ---

    private static class RpcInvocationHandler<T> implements InvocationHandler {

        private final Invoker<T> invoker;

        RpcInvocationHandler(Invoker<T> invoker) {
            this.invoker = invoker;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle Object methods locally
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(invoker, args);
            }

            RpcRequest request = new RpcRequest();
            request.setServiceName(invoker.getInterface().getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setArguments(args != null ? args : new Object[0]);

            CompletableFuture<RpcResponse> future = invoker.invoke(request);

            // If caller expects CompletableFuture, return directly
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                return future.thenApply(RpcResponse::getResult);
            }

            // Blocking call
            RpcResponse response = future.get();
            if (response.hasException()) {
                throw response.getException();
            }
            return response.getResult();
        }
    }
}
