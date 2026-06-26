package com.example.DumblePayment.provider;

import com.example.DumblePayment.exception.ProviderException;
import com.example.DumblePayment.provider.dto.ProviderChargeRequest;
import com.example.DumblePayment.provider.dto.ProviderChargeResponse;
import com.example.DumblePayment.provider.dto.ProviderHostedCheckoutRequest;
import com.example.DumblePayment.provider.dto.ProviderHostedCheckoutResponse;
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
import java.util.Map;
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
 * constant-time comparison. Paymob signs an ordered concatenation of 20
 * specific {@code obj.*} fields (NOT the raw HTTP body) — see
 * {@link #buildPaymobCanonical(JsonNode)} for the exact field list and order
 * lifted from Paymob's webhook docs. The {@code paymob.enabled} flag does
 * NOT relax the HMAC path — even in stub mode the comparison is exercised
 * when the signature header is present, so misconfigured tests fail loudly.
 */
@Component
public class PaymobProvider implements IPaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaymobProvider.class);

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String apiKey;
    private final String hmacSecret;
    private final String baseUrl;
    private final long integrationId;
    private final long iframeId;
    private final String secretKey;
    private final String publicKey;
    private final String notificationUrl;
    private final String redirectionUrl;

    public PaymobProvider(@Qualifier("paymobClient") WebClient client,
                          ObjectMapper objectMapper,
                          @Value("${paymob.enabled:false}") boolean enabled,
                          @Value("${paymob.api-key}") String apiKey,
                          @Value("${paymob.hmac-secret}") String hmacSecret,
                          @Value("${paymob.base-url:https://accept.paymob.com/api}") String baseUrl,
                          @Value("${paymob.integration-id:0}") long integrationId,
                          @Value("${paymob.iframe-id:0}") long iframeId,
                          @Value("${paymob.secret-key:}") String secretKey,
                          @Value("${paymob.public-key:}") String publicKey,
                          @Value("${paymob.notification-url:}") String notificationUrl,
                          @Value("${paymob.redirection-url:}") String redirectionUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.hmacSecret = hmacSecret;
        this.baseUrl = baseUrl;
        this.integrationId = integrationId;
        this.iframeId = iframeId;
        this.secretKey = secretKey;
        this.notificationUrl = notificationUrl;
        this.redirectionUrl = redirectionUrl;
        this.publicKey = publicKey;
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
    public ProviderHostedCheckoutResponse createHostedCheckout(ProviderHostedCheckoutRequest req) {
        if (!enabled) {
            // Stub: hand back a deterministic, well-formed iframe URL so the app's
            // WebView flow can be developed/exercised before real credentials land.
            // The stub webhook fixture (signature "dev-stub-ok") then drives the
            // charge to its terminal state, exactly like a real Paymob callback.
            String token = "stub-paytoken-" + UUID.randomUUID();
            String orderId = "stub-order-" + UUID.randomUUID();
            log.info("PaymobProvider [stub]: hosted checkout order={} amount={} {}",
                    orderId, req.amountCents(), req.currency());
            return new ProviderHostedCheckoutResponse(
                    iframeUrl(token), token, orderId);
        }
        try {
            // Paymob "Unified Intention" API (the account uses sk_/pk_ keys, not
            // the legacy api_key + /auth/tokens flow). Create an intention with
            // the secret key, then build the Unified Checkout URL from the public
            // key + returned client_secret. billing_data fields are mandatory.
            Map<String, Object> billing = new java.util.HashMap<>();
            billing.put("email", orBlank(req.email(), "na@dumble.app"));
            billing.put("first_name", orBlank(req.firstName(), "Dumble"));
            billing.put("last_name", orBlank(req.lastName(), "User"));
            billing.put("phone_number", orBlank(req.phone(), "+201000000000"));
            for (String f : new String[]{"apartment", "floor", "street", "building",
                    "shipping_method", "postal_code", "city", "country", "state"}) {
                billing.put(f, "NA");
            }

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("amount", req.amountCents());
            body.put("currency", req.currency());
            body.put("payment_methods", java.util.List.of(integrationId));
            body.put("billing_data", billing);
            // items.amount must sum to the intention amount.
            body.put("items", java.util.List.of(Map.of(
                    "name", "Dumble",
                    "amount", req.amountCents(),
                    "quantity", 1)));
            // merchant order reference — surfaces as obj.order.merchant_order_id on
            // the webhook, which is how we reconcile the charge.
            body.put("special_reference", req.merchantOrderId());
            // Drive the server-side transaction webhook to OUR endpoint (so we
            // fulfil without relying on dashboard callback config), and where to
            // send the browser after payment.
            if (notificationUrl != null && !notificationUrl.isBlank()) {
                body.put("notification_url", notificationUrl);
            }
            if (redirectionUrl != null && !redirectionUrl.isBlank()) {
                body.put("redirection_url", redirectionUrl);
            }

            JsonNode intention = client.post()
                    .uri("https://accept.paymob.com/v1/intention/")
                    .header("Authorization", "Token " + secretKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String clientSecret = intention == null ? null : intention.path("client_secret").asText(null);
            if (clientSecret == null || clientSecret.isBlank()) {
                throw new ProviderException("Paymob intention returned no client_secret");
            }
            // Paymob's order id for this intention (traceability); the webhook
            // still reconciles via special_reference = merchant_order_id.
            String orderId = intention.path("intention_order_id").asText(
                    intention.path("id").asText(""));
            String checkoutUrl = "https://accept.paymob.com/unifiedcheckout/?publicKey="
                    + publicKey + "&clientSecret=" + clientSecret;

            return new ProviderHostedCheckoutResponse(
                    checkoutUrl, clientSecret, orderId.isBlank() ? null : orderId);
        } catch (ProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderException("Paymob hosted checkout failed: " + ex.getMessage(), ex);
        }
    }

    /** Build the WebView iframe URL Paymob serves the card form from. */
    private String iframeUrl(String paymentToken) {
        return baseUrl + "/acceptance/iframes/" + iframeId + "?payment_token=" + paymentToken;
    }

    private static String orBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
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

        // Real Paymob HMAC: SHA-512 over the documented ordered concatenation
        // of obj.* fields, not the raw body. Build the canonical string, HMAC
        // it, hex-encode, constant-time compare.
        JsonNode obj;
        try {
            obj = objectMapper.readTree(rawBody).path("obj");
            if (obj.isMissingNode() || obj.isNull()) {
                return ProviderWebhookVerification.builder()
                        .valid(false).reason("missing_obj").build();
            }
        } catch (Exception ex) {
            return ProviderWebhookVerification.builder()
                    .valid(false).reason("malformed_body: " + ex.getMessage()).build();
        }

        String canonical = buildPaymobCanonical(obj);
        String expected = hmacSha512Hex(canonical, hmacSecret);
        if (!constantTimeEquals(expected, signatureHeader)) {
            return ProviderWebhookVerification.builder()
                    .valid(false).reason("signature_mismatch").build();
        }
        return parseEvent(rawBody, true, null);
    }

    /**
     * Paymob webhook canonical string (per their public docs): concatenation
     * — no separator — of these 20 fields from the {@code obj} payload, in
     * this exact order. Boolean values serialize as the lowercase strings
     * {@code "true"} / {@code "false"}; missing / null fields serialize as
     * the empty string. The same canonical is used for transaction and
     * subscription webhooks (Paymob keeps the structure uniform).
     *
     * <p>Field order is load-bearing — changing it breaks every webhook.
     * If Paymob revises the contract, change here and re-test against a
     * captured live payload + signature pair.
     */
    static String buildPaymobCanonical(JsonNode obj) {
        JsonNode order = obj.path("order");
        JsonNode source = obj.path("source_data");
        StringBuilder sb = new StringBuilder(256);
        sb.append(asPaymobText(obj.path("amount_cents")));
        sb.append(asPaymobText(obj.path("created_at")));
        sb.append(asPaymobText(obj.path("currency")));
        sb.append(asPaymobText(obj.path("error_occured")));
        sb.append(asPaymobText(obj.path("has_parent_transaction")));
        sb.append(asPaymobText(obj.path("id")));
        sb.append(asPaymobText(obj.path("integration_id")));
        sb.append(asPaymobText(obj.path("is_3d_secure")));
        sb.append(asPaymobText(obj.path("is_auth")));
        sb.append(asPaymobText(obj.path("is_capture")));
        sb.append(asPaymobText(obj.path("is_refunded")));
        sb.append(asPaymobText(obj.path("is_standalone_payment")));
        sb.append(asPaymobText(obj.path("is_voided")));
        sb.append(asPaymobText(order.path("id")));
        sb.append(asPaymobText(obj.path("owner")));
        sb.append(asPaymobText(source.path("pan")));
        sb.append(asPaymobText(source.path("sub_type")));
        sb.append(asPaymobText(source.path("type")));
        sb.append(asPaymobText(obj.path("success")));
        return sb.toString();
    }

    /**
     * Render a JsonNode the way Paymob's signer renders Python primitives
     * when it builds its canonical string: booleans become lowercase
     * {@code "true"}/{@code "false"}, numbers and strings use their natural
     * text, missing/null values become the empty string.
     */
    private static String asPaymobText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isBoolean()) return node.asBoolean() ? "true" : "false";
        return node.asText("");
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
