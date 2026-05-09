package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.SellerLifecycle;
import com.example.DumbleSubscription.domain.enums.EscrowStatus;
import com.example.DumbleSubscription.domain.enums.SellerStatus;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.event.OutboxWriter;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EscrowEntryRepository;
import com.example.DumbleSubscription.repository.SellerLifecycleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages SellerLifecycle (Sections 16, 17, 18, 19). Source of truth for
 * "is this seller accepting new subscriptions" and the freeze→ban transition.
 */
@Service
public class SellerLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SellerLifecycleService.class);

    private final SellerLifecycleRepository repository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final EscrowEntryRepository escrowEntryRepository;
    private final RefundService refundService;
    private final OutboxWriter outboxWriter;
    private final AuditLogger auditLogger;

    public SellerLifecycleService(SellerLifecycleRepository repository,
                                  BundleSubscriptionRepository bundleSubscriptionRepository,
                                  EscrowEntryRepository escrowEntryRepository,
                                  @Lazy RefundService refundService,
                                  OutboxWriter outboxWriter,
                                  AuditLogger auditLogger) {
        this.repository = repository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.escrowEntryRepository = escrowEntryRepository;
        this.refundService = refundService;
        this.outboxWriter = outboxWriter;
        this.auditLogger = auditLogger;
    }

    public boolean canAcceptNewSubscriptions(UUID sellerId) {
        return repository.findById(sellerId)
                .map(s -> s.getStatus() == SellerStatus.ACTIVE)
                .orElse(true);     // no row = ACTIVE
    }

    public boolean canFirePayouts(UUID sellerId) {
        return repository.findById(sellerId)
                .map(s -> s.getStatus() != SellerStatus.FROZEN
                        && s.getStatus() != SellerStatus.BANNED)
                .orElse(true);
    }

    @Transactional
    public SellerLifecycle freeze(UUID sellerId, String reason) {
        Instant now = Instant.now();
        SellerLifecycle lifecycle = loadOrCreate(sellerId, now);
        if (lifecycle.getStatus() == SellerStatus.BANNED) {
            throw new BusinessRuleViolationException("Seller is already banned");
        }
        lifecycle.setStatus(SellerStatus.FROZEN);
        lifecycle.setFrozenAt(now);
        lifecycle.setFrozenReason(reason);
        // Decision 16.2 — 7-day window
        lifecycle.setFrozenUntil(now.plus(7, ChronoUnit.DAYS));
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "SellerFrozen", "ADMIN", null, reason, null);
        outboxWriter.write("SellerFrozen", "subscription.seller.frozen",
                Map.of("sellerId", sellerId, "frozenUntil", lifecycle.getFrozenUntil(), "reason", reason));
        return lifecycle;
    }

    @Transactional
    public SellerLifecycle unfreeze(UUID sellerId, String adminNote) {
        SellerLifecycle lifecycle = repository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller has no lifecycle record"));
        if (lifecycle.getStatus() != SellerStatus.FROZEN) {
            throw new BusinessRuleViolationException("Seller is not frozen");
        }
        Instant now = Instant.now();
        lifecycle.setStatus(SellerStatus.ACTIVE);
        lifecycle.setFrozenAt(null);
        lifecycle.setFrozenReason(null);
        lifecycle.setFrozenUntil(null);
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "SellerUnfrozen", "ADMIN", null, adminNote, null);
        outboxWriter.write("SellerUnfrozen", "subscription.seller.unfrozen", Map.of("sellerId", sellerId));
        return lifecycle;
    }

    @Transactional
    public SellerLifecycle ban(UUID sellerId, String reason) {
        Instant now = Instant.now();
        SellerLifecycle lifecycle = loadOrCreate(sellerId, now);
        if (lifecycle.getStatus() == SellerStatus.BANNED) {
            return lifecycle;       // idempotent
        }
        lifecycle.setStatus(SellerStatus.BANNED);
        lifecycle.setBannedAt(now);
        lifecycle.setBanReason(reason);
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "SellerBanned", "ADMIN", null, reason, null);
        outboxWriter.write("SellerBanned", "subscription.seller.banned",
                Map.of("sellerId", sellerId, "reason", reason));

        // Decision 16.3 — refund all unreleased escrow.
        refundService.refundOnSellerBan(sellerId, reason);
        return lifecycle;
    }

    @Transactional
    public SellerLifecycle startWindingDown(UUID sellerId, String reason) {
        Instant now = Instant.now();
        SellerLifecycle lifecycle = loadOrCreate(sellerId, now);
        if (lifecycle.getStatus() == SellerStatus.BANNED) {
            throw new BusinessRuleViolationException("Banned sellers cannot wind down");
        }
        lifecycle.setStatus(SellerStatus.WINDING_DOWN);
        lifecycle.setWindingDownAt(now);
        lifecycle.setWindingDownReason(reason);
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "WindingDownStarted", "ADMIN", null, reason, null);
        outboxWriter.write("SellerWindingDown", "subscription.seller.winding-down",
                Map.of("sellerId", sellerId, "reason", reason));
        return lifecycle;
    }

    @Transactional
    public SellerLifecycle revertWindingDown(UUID sellerId, String adminNote) {
        SellerLifecycle lifecycle = repository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller has no lifecycle record"));
        if (lifecycle.getStatus() != SellerStatus.WINDING_DOWN) {
            throw new BusinessRuleViolationException("Seller is not winding down");
        }
        Instant now = Instant.now();
        lifecycle.setStatus(SellerStatus.ACTIVE);
        lifecycle.setWindingDownAt(null);
        lifecycle.setWindingDownReason(null);
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "WindingDownReverted", "ADMIN", null, adminNote, null);
        outboxWriter.write("SellerWindingDownReverted", "subscription.seller.winding-down-reverted",
                Map.of("sellerId", sellerId));
        return lifecycle;
    }

    @Transactional
    public SellerLifecycle close(UUID sellerId) {
        Instant now = Instant.now();
        SellerLifecycle lifecycle = loadOrCreate(sellerId, now);
        // Decision 18.1 — block close if any active subs.
        long active = bundleSubscriptionRepository.findBySellerIdAndStatus(sellerId, SubscriptionStatus.ACTIVE).size();
        if (active > 0) {
            throw new BusinessRuleViolationException(
                    "You have active subscriptions — contact support to wind down");
        }
        // Decision 19.2 — honor the "Closed (escrow pending)" intermediate state when
        // there's still HELD or AVAILABLE escrow draining for this seller.
        long pendingEscrow = escrowEntryRepository.countBySellerIdAndStatusIn(
                sellerId, Set.of(EscrowStatus.HELD, EscrowStatus.AVAILABLE));
        if (pendingEscrow > 0) {
            lifecycle.setStatus(SellerStatus.CLOSED_ESCROW_PENDING);
            lifecycle.setClosedAt(now);
            lifecycle.setUpdatedAt(now);
            repository.save(lifecycle);
            auditLogger.log(sellerId, "SellerClosing", "USER", sellerId.toString(),
                    "self-deactivate; awaiting escrow drain",
                    Map.of("pendingEscrowEntries", pendingEscrow));
            outboxWriter.write("SellerClosing", "subscription.seller.closing",
                    Map.of("sellerId", sellerId, "reason", "escrow-pending"));
            return lifecycle;
        }
        lifecycle.setStatus(SellerStatus.CLOSED);
        lifecycle.setClosedAt(now);
        lifecycle.setUpdatedAt(now);
        repository.save(lifecycle);

        auditLogger.log(sellerId, "SellerClosed", "USER", sellerId.toString(), "self-deactivate", null);
        outboxWriter.write("SellerClosed", "subscription.seller.closed", Map.of("sellerId", sellerId));
        return lifecycle;
    }

    /**
     * Called from FrozenAutoBanJob. Closes any wind-down seller whose subs are
     * fully drained, AND finalizes "Closed (escrow pending)" sellers once their
     * escrow has all been paid out.
     */
    @Transactional
    public void closeWindingDownIfDrained() {
        Instant now = Instant.now();

        // WINDING_DOWN → CLOSED_ESCROW_PENDING / CLOSED when no active subs left.
        for (SellerLifecycle lifecycle : repository.findByStatus(SellerStatus.WINDING_DOWN)) {
            long active = bundleSubscriptionRepository
                    .findBySellerIdAndStatus(lifecycle.getSellerId(), SubscriptionStatus.ACTIVE).size();
            if (active > 0) continue;

            long pending = escrowEntryRepository.countBySellerIdAndStatusIn(
                    lifecycle.getSellerId(), Set.of(EscrowStatus.HELD, EscrowStatus.AVAILABLE));
            lifecycle.setStatus(pending > 0 ? SellerStatus.CLOSED_ESCROW_PENDING : SellerStatus.CLOSED);
            lifecycle.setClosedAt(now);
            lifecycle.setUpdatedAt(now);
            repository.save(lifecycle);
            outboxWriter.write(pending > 0 ? "SellerClosing" : "SellerClosed",
                    pending > 0 ? "subscription.seller.closing" : "subscription.seller.closed",
                    Map.of("sellerId", lifecycle.getSellerId(),
                            "from", "winding-down",
                            "pendingEscrowEntries", pending));
            log.info("Wind-down complete: seller {} → {} (pending escrow={})",
                    lifecycle.getSellerId(), lifecycle.getStatus(), pending);
        }

        // CLOSED_ESCROW_PENDING → CLOSED when escrow finally drains.
        for (SellerLifecycle lifecycle : repository.findByStatus(SellerStatus.CLOSED_ESCROW_PENDING)) {
            long pending = escrowEntryRepository.countBySellerIdAndStatusIn(
                    lifecycle.getSellerId(), Set.of(EscrowStatus.HELD, EscrowStatus.AVAILABLE));
            if (pending == 0) {
                lifecycle.setStatus(SellerStatus.CLOSED);
                lifecycle.setUpdatedAt(now);
                repository.save(lifecycle);
                outboxWriter.write("SellerClosed", "subscription.seller.closed",
                        Map.of("sellerId", lifecycle.getSellerId(), "from", "escrow-drained"));
                log.info("Escrow drained: seller {} now fully CLOSED", lifecycle.getSellerId());
            }
        }
    }

    private SellerLifecycle loadOrCreate(UUID sellerId, Instant now) {
        return repository.findById(sellerId).orElseGet(() -> {
            SellerLifecycle fresh = new SellerLifecycle();
            fresh.setSellerId(sellerId);
            fresh.setStatus(SellerStatus.ACTIVE);
            fresh.setCreatedAt(now);
            fresh.setUpdatedAt(now);
            return fresh;
        });
    }
}
