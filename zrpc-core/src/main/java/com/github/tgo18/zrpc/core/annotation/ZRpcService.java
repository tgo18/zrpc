package com.github.tgo18.zrpc.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a zRPC service provider implementation.
 * When used with the Spring Boot starter, the annotated bean is
 * automatically exported via the configured protocol.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ZRpcService {

    /** Service interface to export. Inferred from the implementation class if not set. */
    Class<?> interfaceClass() default void.class;

    /** Service version */
    String version() default "";

    /** Service group */
    String group() default "";

    /** Service weight for load balancing */
    int weight() default 100;

    /** Timeout for each invocation in milliseconds */
    int timeout() default 3000;

    /** Whether to register the service in the registry */
    boolean register() default true;
}
