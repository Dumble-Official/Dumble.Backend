package com.example.DumbleAuthentication.repository;

import com.example.DumbleAuthentication.domain.RoleRequest;
import com.example.DumbleAuthentication.domain.RoleRequestStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRequestRepository extends JpaRepository<RoleRequest, UUID> {

    /** A user's own requests, newest first. */
    List<RoleRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Does the user already have a request in one of these (open) states? */
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<RoleRequestStatus> statuses);

    /** Admin queue — all requests, optionally filtered by status. */
    Page<RoleRequest> findByStatus(RoleRequestStatus status, Pageable pageable);

    /**
     * Load a request for an admin decision, taking a row write-lock so two admins
     * acting on the same request serialize: the second blocks until the first
     * commits, then re-reads the now-decided status and is rejected. Mirrors
     * {@link UserRepository#findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoleRequest r where r.id = :id")
    Optional<RoleRequest> findByIdForUpdate(@Param("id") UUID id);
}
