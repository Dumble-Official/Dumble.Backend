package com.example.DumbleSubscription.event;

import com.example.DumbleSubscription.domain.OutboxEvent;
import com.example.DumbleSubscription.domain.enums.OutboxStatus;
import com.example.DumbleSubscription.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox state-machine helper used by {@link OutboxPublisher} and
 * {@link OutboxConfirmCoordinator}. Each transition runs in its own short
 * transaction (REQUIRES_NEW) so the publisher's scheduling thread and the
 * AMQP IO thread (where the confirm callback fires) don't share Hibernate
 * sessions.
 *
 * <p>Mirrors Wallet's persister (Wallet Decision 6.5) for parity — both
 * services publish money-flow events to the same exchange and must survive
 * the same broker-hiccup failure modes.
 */
@Component
public class OutboxPublishingPersister {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishingPersister.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository repository;

    public OutboxPublishingPersister(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Atomically claim a PENDING row: flip to IN_FLIGHT, increment attempts,
     * stamp publishedAt as the publish-attempt time. Returns empty if the
     * row was racing-claimed by another sweep or has already terminalised.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxEvent> claim(UUID id) {
        OutboxEvent e = repository.findByIdForUpdate(id).orElse(null);
        if (e == null || e.getStatus() != OutboxStatus.PENDING) return Optional.empty();
        e.setStatus(OutboxStatus.IN_FLIGHT);
        e.setAttempts(e.getAttempts() + 1);
        e.setPublishedAt(Instant.now());
        return Optional.of(repository.save(e));
    }

    /** Broker ack — terminal success. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConfirmed(UUID id) {
        OutboxEvent e = repository.findById(id).orElse(null);
        if (e == null) return;
        if (e.getStatus() == OutboxStatus.PUBLISHED) return;
        e.setStatus(OutboxStatus.PUBLISHED);
        e.setPublishedAt(Instant.now());
        e.setLastError(null);
        repository.save(e);
    }

    /** Broker nack — retry until budget is exhausted, then terminal FAILED. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNack(UUID id, String reason) {
        OutboxEvent e = repository.findById(id).orElse(null);
        if (e == null) return;
        if (e.getStatus() != OutboxStatus.IN_FLIGHT) return;
        e.setLastError(truncate(reason));
        if (e.getAttempts() >= MAX_ATTEMPTS) {
            e.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event {} permanently FAILED after {} attempts: {}",
                    e.getId(), e.getAttempts(), reason);
        } else {
            e.setStatus(OutboxStatus.PENDING);
            e.setPublishedAt(null);
        }
        repository.save(e);
    }

    /**
     * Mandatory + ReturnsCallback: the broker accepted the message but no
     * queue is bound to the routing key — terminal FAILED so it isn't
     * retried indefinitely.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturned(UUID id, String reason) {
        OutboxEvent e = repository.findById(id).orElse(null);
        if (e == null) return;
        e.setStatus(OutboxStatus.FAILED);
        e.setLastError(truncate("unroutable: " + reason));
        log.error("Outbox event {} unroutable (returned by broker): {}", e.getId(), reason);
        repository.save(e);
    }

    /**
     * Synchronous send-time AmqpException — the bytes never even hit the
     * AMQP client buffer. Treated like a nack.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSendFailed(UUID id, String reason) {
        onNack(id, reason);
    }

    /**
     * Reset rows that flipped IN_FLIGHT but never received a confirm — broker
     * connection died after we sent, app crashed mid-confirm, or a similar
     * lost-callback scenario. Without this, those rows would leak forever.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStuckInFlight(Instant cutoff) {
        List<OutboxEvent> stuck = repository.findStuckInFlight(cutoff);
        for (OutboxEvent e : stuck) {
            e.setStatus(OutboxStatus.PENDING);
            e.setPublishedAt(null);
            e.setLastError(truncate("recovered from stuck IN_FLIGHT (no confirm received)"));
            repository.save(e);
        }
        if (!stuck.isEmpty()) {
            log.warn("Recovered {} stuck IN_FLIGHT outbox row(s) older than {}", stuck.size(), cutoff);
        }
        return stuck.size();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1900 ? s : s.substring(0, 1900);
    }
}
