package com.jzqs.app.common.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOrigins())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }

    private String[] allowedOrigins() {
        String configured = System.getenv("APP_CORS_ALLOWED_ORIGINS");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("app.cors.allowed-origins");
        }
        if (configured == null || configured.isBlank()) {
            // 生产为同域反代（Caddy），默认不开启跨域；确需跨域时通过环境变量显式配置
            return new String[0];
        }
        return Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }
}
