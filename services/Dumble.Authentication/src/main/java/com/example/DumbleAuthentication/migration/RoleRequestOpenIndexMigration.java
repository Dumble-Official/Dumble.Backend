package com.example.DumbleAuthentication.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces "one open role request per user" at the database level. The service
 * pre-checks with exists(), but that is a check-then-insert race: two concurrent
 * submits from the same user could both pass and create two open rows.
 *
 * A plain unique index on user_id would be wrong — APPROVED/REJECTED rows must be
 * allowed to repeat. Postgres partial indexes fit exactly: the uniqueness only
 * covers the open states (PENDING, CHANGES_REQUESTED). The race loser then trips
 * this index and the service maps it back to the same 400 as the pre-check.
 *
 * Idempotent via IF NOT EXISTS. Runs at CommandLineRunner phase (after Hibernate
 * has created/updated the role_requests table under ddl-auto=update).
 */
@Component
@Order(2)
public class RoleRequestOpenIndexMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleRequestOpenIndexMigration.class);

    private final JdbcTemplate jdbc;

    public RoleRequestOpenIndexMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            jdbc.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_role_requests_open_per_user " +
                    "ON role_requests (user_id) " +
                    "WHERE status IN ('PENDING', 'CHANGES_REQUESTED')");
            log.info("Ensured partial unique index ux_role_requests_open_per_user (one open role request per user)");
        } catch (Exception e) {
            // Defensive — never crash boot on the index. If duplicate open rows
            // already exist the CREATE fails; that must be cleaned up manually.
            log.warn("Could not create ux_role_requests_open_per_user: {}", e.getMessage());
        }
    }
}
