package com.example.DumbleSubscription.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Boot-time sanity checks. Per the locked PDFs we must refuse to start when
 * a non-prod environment is misconfigured to use production secrets — billing
 * services pointed at a real provider from a staging cluster is the worst
 * kind of misconfiguration, fail loud at startup.
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);

    /**
     * bug_006 — the previous "isProd = profiles contains 'prod'/'production'"
     * check let any other naming convention (staging, qa, integration,
     * prod-eu, ...) silently accept the committed dev key. Flip the check:
     * a dev-shaped secret is only OK when an explicit dev/test profile is
     * active. Everything else (no profile, staging, qa, prod, ...) refuses
     * to start.
     */
    private static final Set<String> DEV_TEST_PROFILES = Set.of("dev", "test", "local");

    private final Environment environment;
    private final String serviceJwtKey;
    private final String userJwtSecret;
    private final String dbUrl;

    public StartupGuard(Environment environment,
                        @Value("${service-jwt.signing-key}") String serviceJwtKey,
                        @Value("${jwt.secret:}") String userJwtSecret,
                        @Value("${spring.datasource.url}") String dbUrl) {
        this.environment = environment;
        this.serviceJwtKey = serviceJwtKey;
        this.userJwtSecret = userJwtSecret;
        this.dbUrl = dbUrl;
    }

    @PostConstruct
    public void verify() {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean isExplicitDevOrTest = active.stream().anyMatch(DEV_TEST_PROFILES::contains);
        boolean isProd = active.contains("prod") || active.contains("production");

        boolean looksLikeDevServiceKey = serviceJwtKey == null
                || serviceJwtKey.isBlank()
                || serviceJwtKey.contains("dev-only")
                || serviceJwtKey.contains("test")
                || serviceJwtKey.length() < 32;

        if (looksLikeDevServiceKey && !isExplicitDevOrTest) {
            throw new IllegalStateException(
                    "Refusing to start: SERVICE_JWT_SIGNING_KEY looks like a dev/test value but no dev/test profile is active. "
                            + "Set spring.profiles.active=dev|test|local for local development, "
                            + "or provide a real >= 32-byte SERVICE_JWT_SIGNING_KEY for any other environment.");
        }

        // Same protection for the user-JWT secret. Subscription verifies user
        // JWTs against jwt.secret in TokenExtractor — an empty/dev/short value
        // silently rejects every legit user JWT at /me/** and /entry-tokens/scan,
        // or worse, if a committed placeholder happens to decode to >= 32 bytes,
        // any holder of that placeholder mints tokens that validate.
        boolean looksLikeDevUserKey = userJwtSecret == null
                || userJwtSecret.isBlank()
                || userJwtSecret.contains("dev-only")
                || userJwtSecret.contains("test")
                || decodedLength(userJwtSecret) < 32;
        if (looksLikeDevUserKey && !isExplicitDevOrTest) {
            throw new IllegalStateException(
                    "Refusing to start: JWT_SECRET (user-token signing key) looks like a dev/test value "
                            + "but no dev/test profile is active. Provide a real >= 32-byte JWT_SECRET, "
                            + "matched to what the Auth service signs tokens with.");
        }

        if (!isProd && dbUrl != null && dbUrl.toLowerCase().contains("prod")) {
            throw new IllegalStateException(
                    "Refusing to start: non-prod profile is pointed at a database URL containing 'prod'");
        }

        log.info("StartupGuard ✓ active profiles={}", active);
    }

    private static int decodedLength(String base64Secret) {
        try {
            return java.util.Base64.getDecoder().decode(base64Secret).length;
        } catch (IllegalArgumentException ex) {
            // Not Base64 — count the raw byte length; TokenExtractor applies
            // hmacShaKeyFor() which insists on >= 32 raw bytes either way.
            return base64Secret.getBytes().length;
        }
    }
}
