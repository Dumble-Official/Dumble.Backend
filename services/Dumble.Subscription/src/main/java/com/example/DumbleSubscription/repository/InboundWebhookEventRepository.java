package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.InboundWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundWebhookEventRepository extends JpaRepository<InboundWebhookEvent, String> {
}
