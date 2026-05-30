package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.config.RabbitMQConfig;
import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.event.OutboxWriter;
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
 *
 * <p>The announcement is written to the transactional outbox in the same transaction as the delete,
 * so a rolled-back deletion publishes nothing and a committed one cannot lose its event to a broker
 * outage — {@link OutboxWriter} / {@link com.example.DumbleAuthentication.event.OutboxPublisher}.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final String BANNED_USERS_KEY = "banned_users";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final OutboxWriter outboxWriter;

    public AccountDeletionService(UserRepository userRepository,
                                  JwtService jwtService,
                                  StringRedisTemplate redisTemplate,
                                  OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.outboxWriter = outboxWriter;
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

        // Announce via the outbox, in this same transaction: if the delete rolls back the event row
        // rolls back with it, and once committed a background worker delivers it even if the broker
        // is momentarily down. camelCase keys match the .NET AccountDeletedEvent wire format; both
        // values (a UUID and an ISO-8601 instant) are JSON-safe without escaping.
        String payloadJson = String.format(
                "{\"userId\":\"%s\",\"deletedAt\":\"%s\"}", userId, Instant.now().toString());
        outboxWriter.write("AccountDeleted", RabbitMQConfig.ACCOUNT_DELETED_ROUTING_KEY, payloadJson);

        log.info("Deleted account {} ({})", user.getEmail(), userId);
    }
}
