package com.github.tgo18.zrpc.core.extension;

import java.lang.annotation.*;

/**
 * Marks an interface as an SPI extension point.
 * The value specifies the default implementation name.
 *
 * Extension implementations are discovered via:
 * META-INF/zrpc/{interface-fqn}
 *
 * File format: name=com.example.MyImpl
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {
    /** Default implementation name */
    String value() default "";
}
