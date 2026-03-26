package com.github.tgo18.zrpc.example.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Provider application entry point.
 * Run with: mvn spring-boot:run -pl zrpc-example -Dspring-boot.run.profiles=provider
 */
@SpringBootApplication
public class ProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
