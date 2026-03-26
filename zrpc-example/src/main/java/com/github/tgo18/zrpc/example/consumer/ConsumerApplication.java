package com.github.tgo18.zrpc.example.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consumer application entry point.
 * Run with: mvn spring-boot:run -pl zrpc-example -Dspring-boot.run.profiles=consumer
 */
@SpringBootApplication
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
