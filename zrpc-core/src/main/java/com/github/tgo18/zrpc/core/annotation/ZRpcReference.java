package com.github.tgo18.zrpc.core.annotation;

import java.lang.annotation.*;

/**
 * Injects a zRPC consumer proxy into a field.
 * When used with the Spring Boot starter, the framework auto-creates a proxy
 * that routes calls to the remote service via the configured protocol.
 *
 * <pre>
 * {@literal @}ZRpcReference(version = "1.0")
 * private GreeterService greeter;
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface ZRpcReference {

    /** Service interface. Inferred from the field type if not set. */
    Class<?> interfaceClass() default void.class;

    /** Service version */
    String version() default "";

    /** Service group */
    String group() default "";

    /** Timeout in milliseconds */
    int timeout() default 3000;

    /** Number of retries on failure */
    int retries() default 2;

    /** Load balancing strategy name */
    String loadbalance() default "roundrobin";

    /** Whether to use async invocation */
    boolean async() default false;
}
