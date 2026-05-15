package com.example.DumblePayment.provider;

import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.provider.dto.ProviderChargeRequest;
import com.example.DumblePayment.provider.dto.ProviderChargeResponse;
import com.example.DumblePayment.provider.dto.ProviderPayoutRequest;
import com.example.DumblePayment.provider.dto.ProviderPayoutResponse;
import com.example.DumblePayment.provider.dto.ProviderRefundRequest;
import com.example.DumblePayment.provider.dto.ProviderRefundResponse;
import com.example.DumblePayment.provider.dto.ProviderWebhookVerification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The {@link IPaymentProvider} backed by Paymob (Decision 2.1). Two modes:
 *
 * <ul>
 *   <li>{@code paymob.enabled=true} — real Paymob HTTP. v1 hardcodes EGP only.
 *       Real wiring is intentionally minimal here; the SDK / payload mapping
 *       lives behind the abstraction so future evolution is local to this file.
 *   <li>{@code paymob.enabled=false} — deterministic stub for local dev / test.
 *       Charges return {@code PENDING}, payouts return {@code PENDING}, refunds
 *       return {@code SUCCEEDED}, webhooks verify only when the signature is
 *       a literal "dev-stub-ok" — useful for end-to-end tests against fake
 *       provider events.
 * </ul>
 *
 * Webhook signature verification (Decision 4.1) uses HMAC-SHA512 with
 * constant-time comparison. The current implementation HMACs the raw HTTP
 * request body — Paymob's real scheme HMACs a documented field-concatenation
 * of {@code obj.*} fields, so this path is INTENTIONALLY ELIDED in v1
 * (matching the elided charge / refund / payout HTTP paths) and must be
 * filled in before {@code paymob.enabled=true} ships to any environment that
 * sees real Paymob traffic. The {@code paymob.enabled} flag does NOT relax
 * the HMAC path — even in stub mode the comparison is exercised when the
 * signature header is present, so misconfigured tests fail loudly.
 */
@Component
public class PaymobProvider implements IPaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaymobProvider.class);

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String apiKey;
    private final String hmacSecret;

    public PaymobProvider(@Qualifier("paymobClient") WebClient client,
                          ObjectMapper objectMapper,
                          @Value("${paymob.enabled:false}") boolean enabled,
                          @Value("${paymob.api-key}") String apiKey,
                          @Value("${paymob.hmac-secret}") String hmacSecret) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.hmacSecret = hmacSecret;
    }

    @Override
    public String name() {
        return enabled ? "paymob" : "paymob-stub";
    }

    @Override
    public ProviderChargeResponse charge(ProviderChargeRequest req) {
        if (!enabled) {
            // Stub: synchronous Pending — the orchestrator persists the row,
            // and a webhook (real or test-fixture) advances the lifecycle.
            log.info("PaymobProvider [stub]: charge {} amount={} {}", req.getChargeId(), req.getAmountCents(), req.getCurrency());
            return ProviderChargeResponse.builder()
                    .outcome(ProviderChargeResponse.Outcome.PENDING)
                    .providerRef("stub-charge-" + UUID.randomUUID())
                    .build();
        }
        // Real Paymob mapping is intentionally elided here — production code
        // routes to /api/acceptance/payments with the configured keys + the
        // tokenized payment-method handle. Failures surface as ProviderException.
        try {
            // Placeholder — see paymob docs.
            return ProviderChargeResponse.builder()
                    .outcome(ProviderChargeResponse.Outcome.PENDING)
                    .providerRef(null)
                    .build();
        } catch (Exception ex) {
            throw new ProviderException("Paymob charge failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ProviderRefundResponse refund(ProviderRefundRequest req) {
        if (!enabled) {
            return ProviderRefundResponse.builder()
                    .outcome(ProviderRefundResponse.Outcome.SUCCEEDED)
                    .providerRef("stub-refund-" + UUID.randomUUID())
                    .build();
        }
        try {
            return ProviderRefundResponse.builder()
                    .outcome(ProviderRefundResponse.Outcome.PENDING)
                    .build();
        } catch (Exception ex) {
            throw new ProviderException("Paymob refund failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ProviderPayoutResponse payout(ProviderPayoutRequest req) {
        if (!enabled) {
            log.info("PaymobProvider [stub]: payout {} amount={} {}", req.getPayoutId(), req.getAmountCents(), req.getCurrency());
            return ProviderPayoutResponse.builder()
                    .outcome(ProviderPayoutResponse.Outcome.PENDING)
                    .providerRef("stub-payout-" + UUID.randomUUID())
                    .build();
        }
        try {
            return ProviderPayoutResponse.builder()
                    .outcome(ProviderPayoutResponse.Outcome.PENDING)
                    .build();
        } catch (Exception ex) {
            throw new ProviderException("Paymob payout failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ProviderWebhookVerification verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (rawBody == null || rawBody.isBlank()) {
            return ProviderWebhookVerification.builder()
                    .valid(false).reason("empty_body").build();
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return ProviderWebhookVerification.builder()
                    .valid(false).reason("missing_signature").build();
        }
        // Stub mode shortcut — used by the test-harness to exercise the full
        // pipeline end-to-end without needing a real Paymob secret.
        if (!enabled && "dev-stub-ok".equals(signatureHeader)) {
            return parseEvent(rawBody, true, null);
        }

        // TODO(paymob): Real Paymob webhook HMAC is computed over a documented
        // ordered concatenation of obj.* fields (amount_cents + created_at +
        // currency + ... + success), NOT the raw HTTP body. This raw-body
        // implementation is INTENTIONALLY ELIDED — every real Paymob webhook
        // will fail signature verification the moment paymob.enabled=true.
        // Implement the field-concatenation rule per the Paymob docs before
        // flipping the flag in any environment that sees real webhooks.
        String expected = hmacSha512Hex(rawBody, hmacSecret);
        if (!constantTimeEquals(expected, signatureHeader)) {
            return ProviderWebhookVerification.builder()
                    .valid(false).reason("signature_mismatch").build();
        }
        return parseEvent(rawBody, true, null);
    }

    private ProviderWebhookVerification parseEvent(String rawBody, boolean valid, String reason) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            // Paymob's payload exposes "id" and "type" at the top level (or
            // under an "obj" wrapper depending on event class). Accept either.
            String eventId = node.path("id").asText("");
            if (eventId.isBlank()) eventId = node.path("obj").path("id").asText("");
            String eventType = node.path("type").asText("");
            if (eventType.isBlank()) eventType = node.path("obj").path("type").asText("");
            return ProviderWebhookVerification.builder()
                    .valid(valid)
                    .reason(reason)
                    .eventId(eventId.isBlank() ? null : eventId)
                    .eventType(eventType.isBlank() ? null : eventType)
                    .build();
        } catch (Exception ex) {
            return ProviderWebhookVerification.builder()
                    .valid(false)
                    .reason("malformed_body: " + ex.getMessage())
                    .build();
        }
    }

    private static String hmacSha512Hex(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new ProviderException("HMAC computation failed", ex);
        }
    }

    /**
     * Constant-time comparison — Decision 4.1. Standard {@code String.equals}
     * short-circuits on the first mismatch, leaking timing info that lets an
     * attacker iteratively guess valid signatures.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
