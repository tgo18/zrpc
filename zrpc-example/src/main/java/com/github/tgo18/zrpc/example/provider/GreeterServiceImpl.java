package com.github.tgo18.zrpc.example.provider;

import com.github.tgo18.zrpc.core.annotation.ZRpcService;
import com.github.tgo18.zrpc.example.api.GreeterService;

/**
 * Provider implementation of {@link GreeterService}.
 * Annotated with {@link ZRpcService} so the Spring Boot starter auto-exports it.
 */
@ZRpcService(interfaceClass = GreeterService.class, version = "1.0", weight = 100)
public class GreeterServiceImpl implements GreeterService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! (from zRPC provider)";
    }

    @Override
    public String sayGoodbye(String name) {
        return "Goodbye, " + name + "! See you next time.";
    }
}
