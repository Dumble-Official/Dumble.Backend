package com.example.DumbleWallet.repository;

import com.example.DumbleWallet.domain.InboundListenerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundListenerEventRepository extends JpaRepository<InboundListenerEvent, String> {
}
