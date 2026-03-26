package com.github.tgo18.zrpc.example.consumer;

import com.github.tgo18.zrpc.core.annotation.ZRpcReference;
import com.github.tgo18.zrpc.example.api.GreeterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Consumer that calls {@link GreeterService} via zRPC.
 * The {@link ZRpcReference} field is injected by {@code ZRpcReferenceInjector}.
 */
@Component
public class GreeterConsumer {

    private static final Logger log = LoggerFactory.getLogger(GreeterConsumer.class);

    @ZRpcReference(version = "1.0", loadbalance = "roundrobin", timeout = 5000)
    private GreeterService greeterService;

    @Bean
    public ApplicationRunner greeterRunner() {
        return args -> {
            for (String name : new String[]{"Alice", "Bob", "Charlie"}) {
                String hello = greeterService.sayHello(name);
                String bye   = greeterService.sayGoodbye(name);
                log.info("Response: {}", hello);
                log.info("Response: {}", bye);
            }
        };
    }
}
