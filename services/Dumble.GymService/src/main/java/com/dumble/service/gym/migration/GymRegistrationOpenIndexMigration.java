package com.dumble.service.gym.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Enforces "one open gym registration per applicant" at the database level. The
 * service pre-checks with exists(), but that is a check-then-insert race: two
 * concurrent submits from the same applicant could both pass and create two open
 * rows.
 *
 * MySQL has no partial indexes, so we use a generated column that carries the
 * applicant id only while the registration is open (PENDING / CHANGES_REQUESTED)
 * and is NULL otherwise, with a UNIQUE key on it. MySQL allows many NULLs in a
 * unique index, so closed registrations may repeat while at most one open row per
 * applicant is allowed. The race loser trips this key and the service maps it
 * back to the same 400 as the pre-check.
 *
 * Idempotent: guarded by information_schema lookups (MySQL has no reliable
 * IF NOT EXISTS for ADD COLUMN / ADD KEY). Runs after Hibernate has created the
 * table under ddl-auto=update; ddl-auto=update leaves this extra column/key alone
 * on later boots.
 */
@Component
@Order(1)
public class GymRegistrationOpenIndexMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GymRegistrationOpenIndexMigration.class);

    private final JdbcTemplate jdbc;

    public GymRegistrationOpenIndexMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            Integer hasColumn = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'gym_registrations' " +
                    "AND column_name = 'open_applicant_id'", Integer.class);
            if (hasColumn == null || hasColumn == 0) {
                jdbc.execute(
                        "ALTER TABLE gym_registrations ADD COLUMN open_applicant_id binary(16) " +
                        "GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','CHANGES_REQUESTED') " +
                        "THEN applicant_id ELSE NULL END) VIRTUAL");
                log.info("Added generated column gym_registrations.open_applicant_id");
            }

            Integer hasIndex = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'gym_registrations' " +
                    "AND index_name = 'ux_gym_reg_open_per_applicant'", Integer.class);
            if (hasIndex == null || hasIndex == 0) {
                jdbc.execute(
                        "ALTER TABLE gym_registrations " +
                        "ADD UNIQUE KEY ux_gym_reg_open_per_applicant (open_applicant_id)");
                log.info("Added unique key ux_gym_reg_open_per_applicant (one open registration per applicant)");
            }
        } catch (Exception e) {
            // Defensive — never crash boot on the constraint. If duplicate open
            // rows already exist the ADD KEY fails; that must be cleaned up manually.
            log.warn("Could not ensure one-open-registration constraint: {}", e.getMessage());
        }
    }
}
