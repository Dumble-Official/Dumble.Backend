package com.example.DumbleAuthentication.repository;

import com.example.DumbleAuthentication.domain.RefreshToken;
import com.example.DumbleAuthentication.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    void deleteByToken(String token);
}
