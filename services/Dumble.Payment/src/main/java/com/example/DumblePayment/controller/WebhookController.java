package com.example.DumblePayment.controller;

import com.example.DumblePayment.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Decisions 4.1 + 4.3 — Phase 1 fast ACK. Whitelisted in {@code SecurityConfig}
 * because Paymob doesn't carry our system JWT; HMAC verification inside
 * {@link WebhookService#receive} is the auth mechanism here.
 *
 * Accepts the raw body as a String to preserve the exact bytes for HMAC
 * verification — Jackson auto-deserialization would re-serialize the body
 * (different whitespace, key ordering) and break the signature compare.
 */
@RestController
@RequestMapping("/payment/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/paymob", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Void> paymob(@RequestHeader(name = "X-Paymob-Signature", required = false)
                                       String signature,
                                       @RequestBody String rawBody,
                                       HttpServletRequest req) {
        // Paymob sometimes uses a different header name in their dashboard
        // configuration; accept the most common alternate too.
        String sig = signature;
        if (sig == null || sig.isBlank()) {
            sig = req.getHeader("hmac");
        }
        webhookService.receive(rawBody, sig);
        // Decision 4.3 — fast 200 ACK. Phase-2 work runs async.
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
