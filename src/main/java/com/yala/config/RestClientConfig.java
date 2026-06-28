package com.yala.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exposes a {@link RestClient.Builder} bean for components that call external
 * HTTP APIs (e.g. Didit identity verification in {@code IdentityService}).
 * Kept conditional so an auto-configured builder, when present, still wins.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
