package com.example.DumbleWallet.config;

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
 * Boot-time sanity checks. A wallet service silently running with the
 * committed dev signing key in any non-dev profile is the worst kind of
 * misconfiguration on a money path — fail loud at startup.
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);
    private static final Set<String> DEV_TEST_PROFILES = Set.of("dev", "test", "local");

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
        List<String> active = Arrays.asList(environment.getActiveProfiles());
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
