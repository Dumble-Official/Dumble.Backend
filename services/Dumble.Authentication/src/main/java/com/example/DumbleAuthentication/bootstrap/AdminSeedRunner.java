package com.example.DumbleAuthentication.bootstrap;

import com.example.DumbleAuthentication.domain.AuthProvider;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;
import com.example.DumbleAuthentication.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the very first {@code ADMIN} user on a fresh install, reading
 * credentials from environment variables. Without this, every admin endpoint
 * ({@code /admin/**}, ban management, etc.) returns 403 for every caller
 * because the user table is empty after Hibernate creates the schema.
 *
 * <p>Idempotent — if a user with the configured email already exists, the
 * runner logs a notice and exits. Safe to leave enabled in production with
 * a real password; once the row exists, subsequent boots are no-ops.
 *
 * <p>Gated by {@code auth.bootstrap.admin.enabled=true} so an environment
 * that doesn't want bootstrap (e.g. once an admin has been created and the
 * env vars rotated out) can simply turn it off without removing the bean.
 */
@Component
@ConditionalOnProperty(name = "auth.bootstrap.admin.enabled", havingValue = "true")
public class AdminSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public AdminSeedRunner(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${auth.bootstrap.admin.email:}") String email,
                           @Value("${auth.bootstrap.admin.password:}") String password,
                           @Value("${auth.bootstrap.admin.first-name:Platform}") String firstName,
                           @Value("${auth.bootstrap.admin.last-name:Admin}") String lastName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email == null || email.isBlank()) {
            log.warn("AdminSeedRunner enabled but auth.bootstrap.admin.email is empty — skipping.");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("AdminSeedRunner enabled but auth.bootstrap.admin.password is empty — skipping.");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap: user '{}' already exists — no action taken.", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setUserType(UserType.ADMIN);
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Admin bootstrap: seeded initial ADMIN user '{}'.", email);
    }
}
