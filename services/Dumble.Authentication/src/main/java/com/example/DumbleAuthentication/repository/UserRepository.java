package com.example.DumbleAuthentication.repository;

import com.example.DumbleAuthentication.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByIsActive(boolean isActive);

    /**
     * Pessimistic-write lock on the user row. Used by JwtService.generateRefreshToken
     * to serialize concurrent refresh-token generation for the same user — without
     * it two parallel `deleteByUser + save` calls both insert and leave two live
     * refresh tokens for one user.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
