package com.manuelorg.cross_pesa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer customizePageable() {
        return p -> {
            // If the frontend doesn't send ?size=X, default to 10
            p.setFallbackPageable(org.springframework.data.domain.PageRequest.of(0, 10));

            // SECURITY: Hard limit the maximum page size to 10
            p.setMaxPageSize(10);
        };
    }
}
