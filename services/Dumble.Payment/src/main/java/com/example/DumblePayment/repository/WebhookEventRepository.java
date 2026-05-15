package com.example.DumblePayment.repository;

import com.example.DumblePayment.domain.WebhookEvent;
import com.example.DumblePayment.domain.enums.WebhookProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    List<WebhookEvent> findByProcessingStatusOrderByReceivedAtAsc(WebhookProcessingStatus status, Pageable pageable);
}
