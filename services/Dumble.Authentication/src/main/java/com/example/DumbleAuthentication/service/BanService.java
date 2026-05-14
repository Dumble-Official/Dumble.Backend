package com.example.DumbleAuthentication.service;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class BanService {

    private static final Logger log = LoggerFactory.getLogger(BanService.class);
    private static final String BANNED_USERS_KEY = "banned_users";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;

    public BanService(UserRepository userRepository,
                      StringRedisTemplate redisTemplate,
                      JwtService jwtService) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
    }

    /**
     * On startup, sync all banned users from DB to Redis.
     * This ensures the Gateway's banned-user check works even after Redis restarts.
     */
    @PostConstruct
    public void syncBannedUsersToRedis() {
        try {
            List<User> bannedUsers = userRepository.findByIsActive(false);

            // Clear and rebuild the set
            redisTemplate.delete(BANNED_USERS_KEY);
            for (User user : bannedUsers) {
                redisTemplate.opsForSet().add(BANNED_USERS_KEY, user.getId().toString());
            }

            log.info("Synced {} banned users to Redis", bannedUsers.size());
        } catch (Exception e) {
            log.warn("Failed to sync banned users to Redis on startup: {}", e.getMessage());
        }
    }

    @Transactional
    public void banUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isActive()) {
            throw new IllegalStateException("User is already banned");
        }

        // Order matters: protection must precede persistence so we never have a
        // window where the DB row is flipped but the gateway hasn't been told.
        //
        // 1) Revoke refresh tokens so the user can't mint a new access token via
        //    /api/auth/refresh.
        jwtService.deleteAllUserRefreshTokens(user);

        // 2) Update Redis (gateway's source of truth for the ban check). If this
        //    throws, the @Transactional rollback reverts the DB-side state below.
        //    If the DB write later throws, Redis stays marked — that's fail-closed,
        //    and PostConstruct.syncBannedUsersToRedis reconciles on next restart.
        redisTemplate.opsForSet().add(BANNED_USERS_KEY, userId.toString());

        // 3) Persist the ban in the DB last.
        user.setActive(false);
        userRepository.save(user);

        log.info("Banned user: {} ({})", user.getEmail(), userId);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.isActive()) {
            throw new IllegalStateException("User is not banned");
        }

        // Unban order is reversed from ban: persist DB first so the source of
        // truth is updated before we relax the protection. If Redis remove later
        // throws, the user is "active in DB, banned in Redis" — fail-closed,
        // reconciled at next syncBannedUsersToRedis.
        user.setActive(true);
        userRepository.save(user);

        redisTemplate.opsForSet().remove(BANNED_USERS_KEY, userId.toString());

        log.info("Unbanned user: {} ({})", user.getEmail(), userId);
    }

    public List<User> getBannedUsers() {
        return userRepository.findByIsActive(false);
    }
}
