package com.github.tgo18.zrpc.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * zRPC Admin Console entry point.
 *
 * <p>Default URL: http://localhost:7070
 *
 * <p>Run:
 * <pre>
 *   mvn spring-boot:run -pl zrpc-admin
 * </pre>
 */
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
