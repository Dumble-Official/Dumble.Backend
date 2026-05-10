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

    /**
     * bug_006 — the previous "isProd = profiles contains 'prod'/'production'"
     * check let any other naming convention (staging, qa, integration,
     * prod-eu, ...) silently accept the committed dev key. Flip the check:
     * a dev-shaped secret is only OK when an explicit dev/test profile is
     * active. Everything else (no profile, staging, qa, prod, ...) refuses
     * to start.
     */
    private static final java.util.Set<String> DEV_TEST_PROFILES = java.util.Set.of("dev", "test", "local");

    @PostConstruct
    public void verify() {
        java.util.List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean isExplicitDevOrTest = active.stream().anyMatch(DEV_TEST_PROFILES::contains);
        boolean isProd = active.contains("prod") || active.contains("production");

        boolean looksLikeDevSecret = serviceJwtKey == null
                || serviceJwtKey.isBlank()
                || serviceJwtKey.contains("dev-only")
                || serviceJwtKey.contains("test")
                || serviceJwtKey.length() < 32;

        if (looksLikeDevSecret && !isExplicitDevOrTest) {
            throw new IllegalStateException(
                    "Refusing to start: SERVICE_JWT_SIGNING_KEY looks like a dev/test value but no dev/test profile is active. "
                            + "Set spring.profiles.active=dev|test|local for local development, "
                            + "or provide a real >= 32-byte SERVICE_JWT_SIGNING_KEY for any other environment.");
        }

        if (!isProd && dbUrl != null && dbUrl.toLowerCase().contains("prod")) {
            throw new IllegalStateException(
                    "Refusing to start: non-prod profile is pointed at a database URL containing 'prod'");
        }

        log.info("StartupGuard ✓ active profiles={}", active);
    }
}
