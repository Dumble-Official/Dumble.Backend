package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.OutboxEvent;
import com.example.DumbleSubscription.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable page);
}
