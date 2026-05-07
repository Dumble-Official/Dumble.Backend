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

        // Update DB
        user.setActive(false);
        userRepository.save(user);

        // Sync to Redis — Gateway checks this set
        redisTemplate.opsForSet().add(BANNED_USERS_KEY, userId.toString());

        // Invalidate all refresh tokens so banned user can't get new access tokens
        jwtService.deleteAllUserRefreshTokens(user);

        log.info("Banned user: {} ({})", user.getEmail(), userId);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.isActive()) {
            throw new IllegalStateException("User is not banned");
        }

        // Update DB
        user.setActive(true);
        userRepository.save(user);

        // Remove from Redis
        redisTemplate.opsForSet().remove(BANNED_USERS_KEY, userId.toString());

        log.info("Unbanned user: {} ({})", user.getEmail(), userId);
    }

    public List<User> getBannedUsers() {
        return userRepository.findByIsActive(false);
    }
}
