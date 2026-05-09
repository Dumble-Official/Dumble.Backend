package com.example.DumbleSubscription.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Boot-time sanity checks. Per the locked PDFs we must refuse to start when
 * a non-prod environment is misconfigured to use production secrets — billing
 * services pointed at a real provider from a staging cluster is the worst
 * kind of misconfiguration, fail loud at startup.
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);

    private final Environment environment;
    private final String serviceJwtKey;
    private final String dbUrl;

    public StartupGuard(Environment environment,
                        @Value("${service-jwt.signing-key}") String serviceJwtKey,
                        @Value("${spring.datasource.url}") String dbUrl) {
        this.environment = environment;
        this.serviceJwtKey = serviceJwtKey;
        this.dbUrl = dbUrl;
    }

    @PostConstruct
    public void verify() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("production");
        boolean looksLikeDevSecret = serviceJwtKey == null
                || serviceJwtKey.isBlank()
                || serviceJwtKey.contains("dev-only")
                || serviceJwtKey.contains("test")
                || serviceJwtKey.length() < 16;

        if (isProd && looksLikeDevSecret) {
            throw new IllegalStateException(
                    "Refusing to start: SERVICE_JWT_SIGNING_KEY looks like a dev/test value in a prod profile");
        }

        if (!isProd && dbUrl != null && dbUrl.toLowerCase().contains("prod")) {
            throw new IllegalStateException(
                    "Refusing to start: non-prod profile is pointed at a database URL containing 'prod'");
        }

        log.info("StartupGuard ✓ active profiles={}", Arrays.toString(environment.getActiveProfiles()));
    }
}
