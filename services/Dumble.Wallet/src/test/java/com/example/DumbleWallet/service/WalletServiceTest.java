package com.example.DumbleWallet.service;

import com.example.DumbleWallet.dto.WalletCreditRequest;
import com.example.DumbleWallet.dto.WalletDebitRequest;
import com.example.DumbleWallet.dto.WalletSummaryResponse;
import com.example.DumbleWallet.dto.WalletWriteResponse;
import com.example.DumbleWallet.exception.BusinessRuleViolationException;
import com.example.DumbleWallet.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the credit → debit → summary loop on H2.
 *
 * Note: the append-only enforcement on {@code wallet_entries} is a Postgres
 * trigger declared in V1 migration; H2 with {@code ddl-auto=create-drop} +
 * Flyway disabled (test profile) will NOT install the trigger, so a test
 * suite that needs to verify the trigger should switch to Testcontainers
 * + real Postgres. These tests cover the service-layer semantics only.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WalletServiceTest {

    @Autowired
    private WalletService walletService;

    @Test
    void credit_autoCreatesWalletAndIncrementsBalance() {
        UUID userId = UUID.randomUUID();
        WalletCreditRequest req = req(userId, 1500, "BAN_REFUND", "refund-1", "test refund");

        WalletWriteResponse response = walletService.credit(req);

        assertThat(response.getWalletEntryId()).isNotNull();
        assertThat(response.getNewBalanceCents()).isEqualTo(1500);
        assertThat(response.getCurrency()).isEqualTo("EGP");
    }

    @Test
    void credit_acceptsPascalCaseSource() {
        // Subscription's WalletServiceClient sends "BanRefund"; enum is BAN_REFUND.
        UUID userId = UUID.randomUUID();
        WalletCreditRequest req = req(userId, 500, "BanRefund", "ref-2", null);

        WalletWriteResponse response = walletService.credit(req);

        assertThat(response.getNewBalanceCents()).isEqualTo(500);
    }

    @Test
    void credit_rejectsDebitOnlySource() {
        UUID userId = UUID.randomUUID();
        WalletCreditRequest req = req(userId, 100, "InAppSpend", null, null);

        assertThatThrownBy(() -> walletService.credit(req))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not valid for credit");
    }

    @Test
    void debit_succeedsWhenBalanceCovers() {
        UUID userId = UUID.randomUUID();
        walletService.credit(req(userId, 5000, "BAN_REFUND", "credit-1", null));

        WalletWriteResponse response = walletService.debit(debit(userId, 2000, "InAppSpend", "sub-1"));

        assertThat(response.getNewBalanceCents()).isEqualTo(3000);
    }

    @Test
    void debit_throwsInsufficientWhenBalanceShort() {
        UUID userId = UUID.randomUUID();
        walletService.credit(req(userId, 1000, "BAN_REFUND", "credit-1", null));

        assertThatThrownBy(() -> walletService.debit(debit(userId, 5000, "InAppSpend", "sub-1")))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void debit_throwsInsufficientWhenWalletMissing() {
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> walletService.debit(debit(stranger, 100, "InAppSpend", "sub-1")))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void summary_returnsZeroForUnknownUser() {
        WalletSummaryResponse summary = walletService.summary(UUID.randomUUID());

        assertThat(summary.getAvailableCents()).isZero();
        assertThat(summary.getPendingCents()).isZero();
        assertThat(summary.getCurrency()).isEqualTo("EGP");
        assertThat(summary.getRecentActivity()).isEmpty();
    }

    @Test
    void summary_includesRecentActivityAfterCreditDebit() {
        UUID userId = UUID.randomUUID();
        walletService.credit(req(userId, 3000, "BAN_REFUND", "credit-1", "first"));
        walletService.credit(req(userId, 2000, "CHARGEBACK", "credit-2", "second"));
        walletService.debit(debit(userId, 1500, "InAppSpend", "sub-1"));

        WalletSummaryResponse summary = walletService.summary(userId);

        assertThat(summary.getAvailableCents()).isEqualTo(3500);   // 3000 + 2000 - 1500
        assertThat(summary.getRecentActivity()).hasSize(3);
    }

    @Test
    void debit_thenCredit_keepsLedgerInvariant() {
        UUID userId = UUID.randomUUID();
        walletService.credit(req(userId, 10_000, "BAN_REFUND", "c-1", null));
        walletService.debit(debit(userId, 4_000, "InAppSpend", "sub-1"));
        walletService.credit(req(userId, 1_500, "CHARGEBACK", "c-2", null));
        walletService.debit(debit(userId, 2_000, "InAppSpend", "sub-2"));

        // 10000 - 4000 + 1500 - 2000 = 5500
        assertThat(walletService.summary(userId).getAvailableCents()).isEqualTo(5500);
    }

    private static WalletCreditRequest req(UUID userId, long amount, String source, String ref, String memo) {
        WalletCreditRequest r = new WalletCreditRequest();
        r.setUserId(userId);
        r.setAmountCents(amount);
        r.setSource(source);
        r.setExternalRef(ref);
        r.setMemo(memo);
        return r;
    }

    private static WalletDebitRequest debit(UUID userId, long amount, String source, String ref) {
        WalletDebitRequest r = new WalletDebitRequest();
        r.setUserId(userId);
        r.setAmountCents(amount);
        r.setSource(source);
        r.setExternalRef(ref);
        return r;
    }
}
