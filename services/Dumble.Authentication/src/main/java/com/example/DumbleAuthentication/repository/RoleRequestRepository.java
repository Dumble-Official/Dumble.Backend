package com.example.DumbleAuthentication.repository;

import com.example.DumbleAuthentication.domain.RoleRequest;
import com.example.DumbleAuthentication.domain.RoleRequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoleRequestRepository extends JpaRepository<RoleRequest, UUID> {

    /** A user's own requests, newest first. */
    List<RoleRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Does the user already have a request in one of these (open) states? */
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<RoleRequestStatus> statuses);

    /** Admin queue — all requests, optionally filtered by status. */
    Page<RoleRequest> findByStatus(RoleRequestStatus status, Pageable pageable);
}
