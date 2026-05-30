package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.event.AccountEventPublisher;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Hard-deletes a user account (right-to-be-forgotten) and announces it so other services purge
 * what they hold about the user. Hard delete — not the {@code isActive=false} flag, which means
 * "banned" — genuinely removes the PII from Auth. Only refresh tokens reference the user row, and
 * those are revoked first.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final String BANNED_USERS_KEY = "banned_users";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final AccountEventPublisher eventPublisher;

    public AccountDeletionService(UserRepository userRepository,
                                  JwtService jwtService,
                                  StringRedisTemplate redisTemplate,
                                  AccountEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void deleteOwnAccount(User user) {
        UUID userId = user.getId();

        // Revoke refresh tokens first so no new access token can be minted for the dying account.
        jwtService.deleteAllUserRefreshTokens(user);

        // Drop any ban marker — the row is going away (defensive; a banned user is blocked at the
        // gateway and wouldn't reach this endpoint).
        redisTemplate.opsForSet().remove(BANNED_USERS_KEY, userId.toString());

        // Remove the PII. Refresh tokens are the only rows that FK to the user, handled above.
        userRepository.delete(user);

        // Announce inside the transaction: if the broker is unreachable the publish throws and the
        // whole delete rolls back, so we never erase the account without telling other services.
        eventPublisher.publishAccountDeleted(userId, Instant.now());

        log.info("Deleted account {} ({})", user.getEmail(), userId);
    }
}
