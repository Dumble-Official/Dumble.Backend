package com.example.DumbleGateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Cross-origin policy for the gateway. Origins are explicit, never wildcard:
 * a "*" origin combined with state-changing verbs hands cross-origin sites a
 * free CSRF surface the moment anything else flips allow-credentials on.
 * Configure via {@code cors.allowed-origins} (comma-separated).
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String allowedOriginsCsv) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "cors.allowed-origins must list at least one explicit origin "
                            + "(comma-separated). Wildcards are not accepted.");
        }
        if (this.allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "cors.allowed-origins must not contain '*'. List the real origins explicitly.");
        }
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
