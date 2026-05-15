package com.example.DumbleWallet.service;

import com.example.DumbleWallet.domain.WithdrawalRequest;
import com.example.DumbleWallet.domain.enums.WithdrawalStatus;
import com.example.DumbleWallet.dto.WalletCreditRequest;
import com.example.DumbleWallet.exception.BusinessRuleViolationException;
import com.example.DumbleWallet.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the pessimistic-lock contract that backs the cancel-vs-submitting
 * fix: regardless of which thread wins the row lock, exactly one of
 * {@code cancel} and {@code tryMarkSubmitting} succeeds and the other observes
 * the new state and rejects cleanly. Without the lock, both would race into
 * an optimistic-version conflict and one would surface as a 500 to the user.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WithdrawalCancelRaceTest {

    @Autowired private WalletService walletService;
    @Autowired private WithdrawalPersister persister;
    @Autowired private WithdrawalRequestRepository withdrawalRequestRepository;

    @Test
    void cancelAndTryMarkSubmitting_serializeOnRowLock() throws Exception {
        UUID userId = UUID.randomUUID();
        // Top up so balance covers the withdrawal.
        WalletCreditRequest credit = new WalletCreditRequest();
        credit.setUserId(userId);
        credit.setAmountCents(20_000);
        credit.setSource("BAN_REFUND");
        credit.setExternalRef("seed");
        walletService.credit(credit);

        UUID withdrawalId = createPendingDirectly(userId, 10_000);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        // Two-latch handshake: `ready` confirms both threads have actually
        // been scheduled and are blocked on `start` before we release them,
        // so the race can't be won by a thread the executor hasn't started
        // yet (which would otherwise produce a deterministic outcome and
        // hide bugs in the lock contract).
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> cancelError = new AtomicReference<>();
        AtomicReference<Throwable> submitError = new AtomicReference<>();
        AtomicReference<Boolean> submittingResult = new AtomicReference<>();

        try {
            var cancelTask = pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    persister.cancel(userId, withdrawalId);
                } catch (Throwable t) {
                    cancelError.set(t);
                }
                return null;
            });
            var submitTask = pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    submittingResult.set(persister.tryMarkSubmitting(withdrawalId));
                } catch (Throwable t) {
                    submitError.set(t);
                }
                return null;
            });

            // Wait until both threads are blocked on `start` before releasing.
            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("both threads should be ready within 5s")
                    .isTrue();
            start.countDown();
            cancelTask.get(5, TimeUnit.SECONDS);
            submitTask.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        WithdrawalRequest finalState = withdrawalRequestRepository.findById(withdrawalId).orElseThrow();

        boolean cancelWon = cancelError.get() == null;
        boolean submitWon = submitError.get() == null
                && Boolean.TRUE.equals(submittingResult.get());

        // Exactly one wins, the other rejects cleanly (no optimistic lock 500).
        assertThat(cancelWon ^ submitWon)
                .as("exactly one of cancel/tryMarkSubmitting should succeed")
                .isTrue();

        if (cancelWon) {
            // tryMarkSubmitting must have returned false (status was no longer PENDING)
            // OR thrown a graceful rejection — never an OptimisticLockException.
            assertThat(submittingResult.get()).isFalse();
            assertThat(submitError.get()).isNull();
            assertThat(finalState.getStatus()).isEqualTo(WithdrawalStatus.CANCELLED);
        } else {
            // tryMarkSubmitting won; cancel must have rejected with a clear
            // BusinessRuleViolationException (status != PENDING), not blown up.
            assertThat(cancelError.get())
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThat(finalState.getStatus()).isEqualTo(WithdrawalStatus.SUBMITTING);
        }
    }

    /**
     * Side-step the full requestWithdrawal flow (which would call Payment)
     * by inserting a PENDING WithdrawalRequest directly. The race we're
     * testing is between cancel and tryMarkSubmitting once the row exists.
     * {@code saveAndFlush} commits in its own internal tx — no
     * {@code @Transactional} needed here (and Spring's AOP wouldn't
     * intercept a test-class method anyway).
     */
    UUID createPendingDirectly(UUID userId, long amount) {
        WithdrawalRequest w = new WithdrawalRequest();
        w.setWalletUserId(userId);
        w.setAmountCents(amount);
        w.setCurrency("EGP");
        w.setDestinationJson("{\"type\":\"bank\",\"iban\":\"EG00...\"}");
        w.setStatus(WithdrawalStatus.PENDING);
        Instant now = Instant.now();
        w.setCreatedAt(now);
        w.setUpdatedAt(now);
        return withdrawalRequestRepository.saveAndFlush(w).getId();
    }
}
