package com.example.DumbleSubscription.repository;

import com.example.DumbleSubscription.domain.InboundListenerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundListenerEventRepository extends JpaRepository<InboundListenerEvent, String> {
}
