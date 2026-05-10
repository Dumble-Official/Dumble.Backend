package com.example.DumblePayment.config;

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
 * Boot-time refusals (Decisions 10.2, 10.3). Money services with the wrong
 * secrets in the wrong environment are the worst kind of misconfiguration —
 * fail loud at startup so it never reaches production traffic.
 *
 * Two checks:
 *
 * <ul>
 *   <li>Reject a dev-shaped {@code service-jwt.signing-key} unless an
 *       explicit {@code dev}/{@code test}/{@code local} profile is active.
 *   <li>Reject a Paymob API key with the configured production prefix
 *       (default {@code live_}) unless the active profile is {@code prod}.
 *       This is the "non-prod hits real Paymob" guard from Decision 10.3.
 * </ul>
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);
    private static final Set<String> DEV_TEST_PROFILES = Set.of("dev", "test", "local");

    private final Environment environment;
    private final String serviceJwtKey;
    private final String paymobApiKey;
    private final String paymobProdPrefix;
    private final String dbUrl;

    public StartupGuard(Environment environment,
                        @Value("${service-jwt.signing-key}") String serviceJwtKey,
                        @Value("${paymob.api-key}") String paymobApiKey,
                        @Value("${paymob.prod-key-prefix:live_}") String paymobProdPrefix,
                        @Value("${spring.datasource.url}") String dbUrl) {
        this.environment = environment;
        this.serviceJwtKey = serviceJwtKey;
        this.paymobApiKey = paymobApiKey;
        this.paymobProdPrefix = paymobProdPrefix;
        this.dbUrl = dbUrl;
    }

    @PostConstruct
    public void verify() {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean isDevOrTest = active.stream().anyMatch(DEV_TEST_PROFILES::contains);
        boolean isProd = active.contains("prod") || active.contains("production");

        boolean looksLikeDevServiceKey = serviceJwtKey == null
                || serviceJwtKey.isBlank()
                || serviceJwtKey.contains("dev-only")
                || serviceJwtKey.contains("test")
                || serviceJwtKey.length() < 32;

        if (looksLikeDevServiceKey && !isDevOrTest) {
            throw new IllegalStateException(
                    "Refusing to start: SERVICE_JWT_SIGNING_KEY looks like a dev/test value but no dev/test profile is active. "
                            + "Set spring.profiles.active=dev|test|local for local development, "
                            + "or provide a real >= 32-byte SERVICE_JWT_SIGNING_KEY for any other environment.");
        }

        // Decision 10.3 — non-prod must NOT use a production Paymob key.
        if (paymobApiKey != null && !paymobApiKey.isBlank()
                && paymobApiKey.startsWith(paymobProdPrefix)
                && !isProd) {
            throw new IllegalStateException(
                    "Refusing to start: PAYMOB_API_KEY starts with the production prefix '"
                            + paymobProdPrefix + "' but the active profile is not 'prod'. "
                            + "Real-money Paymob keys must never be used outside production.");
        }

        if (!isProd && dbUrl != null && dbUrl.toLowerCase().contains("prod")) {
            throw new IllegalStateException(
                    "Refusing to start: non-prod profile is pointed at a database URL containing 'prod'");
        }

        log.info("StartupGuard ✓ active profiles={}", active);
    }
}
