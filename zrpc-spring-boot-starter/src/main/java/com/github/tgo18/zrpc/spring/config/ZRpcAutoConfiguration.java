package com.github.tgo18.zrpc.spring.config;

import com.github.tgo18.zrpc.spring.bean.ZRpcReferenceInjector;
import com.github.tgo18.zrpc.spring.bean.ZRpcServiceExporter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for zRPC.
 * Activated automatically via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Registers two beans:
 * <ul>
 *   <li>{@link ZRpcServiceExporter} — scans for {@code @ZRpcService} and exports them</li>
 *   <li>{@link ZRpcReferenceInjector} — injects {@code @ZRpcReference} consumer proxies</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(ZRpcProperties.class)
public class ZRpcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ZRpcServiceExporter zRpcServiceExporter(ZRpcProperties properties) {
        return new ZRpcServiceExporter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ZRpcReferenceInjector zRpcReferenceInjector(ZRpcProperties properties) {
        return new ZRpcReferenceInjector(properties);
    }
}
