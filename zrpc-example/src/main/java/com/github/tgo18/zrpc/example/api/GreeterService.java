package com.github.tgo18.zrpc.example.api;

/**
 * Sample service API shared between provider and consumer.
 * In a real project this would live in its own API jar.
 */
public interface GreeterService {

    /**
     * Returns a greeting for the given name.
     */
    String sayHello(String name);

    /**
     * Returns a farewell message.
     */
    String sayGoodbye(String name);
}
