package com.example.DumblePayment.service;

import com.example.DumblePayment.domain.PaymentMethodToken;
import com.example.DumblePayment.domain.enums.PaymentMethodKind;
import com.example.DumblePayment.dto.PaymentMethodResponse;
import com.example.DumblePayment.dto.TokenizeRequest;
import com.example.DumblePayment.dto.TokenizeResponse;
import com.example.DumblePayment.exception.BusinessRuleViolationException;
import com.example.DumblePayment.repository.PaymentMethodTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Decision 10.1 — Payment never sees raw card numbers; the frontend
 * tokenises directly with Paymob, this endpoint just records the opaque
 * handle so future renewals / repeat purchases can reuse it.
 */
@Service
public class PaymentMethodService {

    private final PaymentMethodTokenRepository repository;

    public PaymentMethodService(PaymentMethodTokenRepository repository) {
        this.repository = repository;
    }

    /** A user's active (non-deleted) saved payment methods. */
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> listActive(UUID userId) {
        return repository.findActiveByUser(userId).stream().map(PaymentMethodResponse::from).toList();
    }

    /** Soft-delete a saved payment method by stamping deletedAt. */
    @Transactional
    public void delete(UUID id) {
        repository.findById(id).ifPresent(t -> {
            if (t.getDeletedAt() == null) {
                t.setDeletedAt(Instant.now());
                repository.save(t);
            }
        });
    }

    @Transactional
    public TokenizeResponse register(TokenizeRequest req) {
        // Idempotent on the token value — re-registering the same handle
        // returns the existing row.
        return repository.findByToken(req.getToken())
                .map(TokenizeResponse::from)
                .orElseGet(() -> TokenizeResponse.from(persist(req)));
    }

    private PaymentMethodToken persist(TokenizeRequest req) {
        PaymentMethodToken t = new PaymentMethodToken();
        t.setUserId(req.getUserId());
        t.setToken(req.getToken());
        t.setMethodType(parseKind(req.getMethodType()));
        t.setLabel(req.getLabel());
        t.setCardBrand(req.getCardBrand());
        t.setLast4(req.getLast4());
        t.setCreatedAt(Instant.now());
        return repository.save(t);
    }

    private PaymentMethodKind parseKind(String raw) {
        try {
            return PaymentMethodKind.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Unknown methodType: " + raw);
        }
    }
}
