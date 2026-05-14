package com.example.DumblePayment.event;

import com.example.DumblePayment.domain.OutboxEvent;
import com.example.DumblePayment.domain.enums.OutboxStatus;
import com.example.DumblePayment.repository.OutboxEventRepository;
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
 */
@Component
public class OutboxPublishingPersister {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishingPersister.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository repository;

    public OutboxPublishingPersister(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxEvent> claim(UUID id) {
        // Pessimistic-write lock serialises concurrent claim() calls for the
        // same id across replicas. Without it, two dispatcher ticks reading
        // the same PENDING row both flip it to IN_FLIGHT in memory and both
        // publish to RabbitMQ — consumer dedup catches the duplicate but the
        // duplicate publish is the bug we don't want to depend on luck for.
        OutboxEvent e = repository.findByIdForUpdate(id).orElse(null);
        if (e == null || e.getStatus() != OutboxStatus.PENDING) return Optional.empty();
        e.setStatus(OutboxStatus.IN_FLIGHT);
        e.setAttempts(e.getAttempts() + 1);
        e.setPublishedAt(Instant.now());
        return Optional.of(repository.save(e));
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturned(UUID id, String reason) {
        OutboxEvent e = repository.findById(id).orElse(null);
        if (e == null) return;
        e.setStatus(OutboxStatus.FAILED);
        e.setLastError(truncate("unroutable: " + reason));
        log.error("Outbox event {} unroutable (returned by broker): {}", e.getId(), reason);
        repository.save(e);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSendFailed(UUID id, String reason) {
        onNack(id, reason);
    }

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
