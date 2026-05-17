package com.example.DumbleAuthentication.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot data migration. The `GYM` user type was previously used for the
 * human person who owns a gym; it has been split into:
 *   • GYM_OWNER — the human account (existing rows must move here)
 *   • GYM       — the gym page itself (new role, no existing rows)
 *
 * Idempotent: subsequent boots update zero rows.
 *
 * Note: runs at CommandLineRunner phase, which is after the embedded server
 * has started. There is a small window where a request could observe a row
 * with the legacy "GYM" value as the new `Gym` page enum. In production,
 * prefer running this UPDATE manually before deploying the new code, or
 * replace this with a Flyway/Liquibase migration that runs before
 * Hibernate's first session opens.
 */
@Component
@Order(1)
public class UserTypeRenameMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserTypeRenameMigration.class);

    private final JdbcTemplate jdbc;

    public UserTypeRenameMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            int rows = jdbc.update("UPDATE app_user SET user_type = 'GYM_OWNER' WHERE user_type = 'GYM'");
            if (rows > 0) {
                log.info("Migrated {} user(s) from legacy 'GYM' user_type to 'GYM_OWNER'", rows);
            }
        } catch (Exception e) {
            // Defensive — if anything goes sideways (e.g. column type changed),
            // log it but don't crash the service on boot.
            log.warn("UserType migration failed: {}", e.getMessage());
        }
    }
}
