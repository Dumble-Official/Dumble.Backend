package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.SubscriptionEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionEventLogRepository extends JpaRepository<SubscriptionEventLog, UUID> {
    List<SubscriptionEventLog> findBySubscriptionIdOrderByTimestampAsc(UUID subscriptionId);
}
