package com.example.DumblePayment.provider;

import com.example.DumblePayment.provider.dto.ProviderChargeRequest;
import com.example.DumblePayment.provider.dto.ProviderChargeResponse;
import com.example.DumblePayment.provider.dto.ProviderPayoutRequest;
import com.example.DumblePayment.provider.dto.ProviderPayoutResponse;
import com.example.DumblePayment.provider.dto.ProviderRefundRequest;
import com.example.DumblePayment.provider.dto.ProviderRefundResponse;
import com.example.DumblePayment.provider.dto.ProviderWebhookVerification;

/**
 * Decision 2.2 — domain code knows nothing about Paymob types. {@code PaymobProvider}
 * is the only implementation in v1; adding Stripe / Kashier later means a new impl
 * and zero changes elsewhere.
 *
 * Each method returns a provider-neutral response or throws
 * {@link com.example.DumblePayment.exception.ProviderException}.
 */
public interface IPaymentProvider {

    /** Make a charge attempt. Result is one of Pending / Succeeded / Failed. */
    ProviderChargeResponse charge(ProviderChargeRequest req);

    /** Issue a refund against a previously-Succeeded charge (Decision 5.2 ORIGINAL_METHOD path). */
    ProviderRefundResponse refund(ProviderRefundRequest req);

    /** Dispatch a payout (used by both Wallet withdrawals and cohort payouts — Decision 6.x). */
    ProviderPayoutResponse payout(ProviderPayoutRequest req);

    /**
     * Verify the HMAC signature on a Paymob webhook payload (Decision 4.1).
     * Implementations must use a constant-time comparison.
     */
    ProviderWebhookVerification verifyWebhookSignature(String rawBody, String signatureHeader);

    /** Identifies the implementation for logging / audit / recon. */
    String name();
}
