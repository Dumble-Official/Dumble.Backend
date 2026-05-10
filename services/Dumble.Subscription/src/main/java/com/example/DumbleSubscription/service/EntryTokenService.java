package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.EntryLog;
import com.example.DumbleSubscription.domain.EntryToken;
import com.example.DumbleSubscription.domain.ParticipantGymNote;
import com.example.DumbleSubscription.domain.enums.EntryDenialReason;
import com.example.DumbleSubscription.domain.enums.EntryResult;
import com.example.DumbleSubscription.domain.enums.EntryTokenStatus;
import com.example.DumbleSubscription.domain.enums.SellerType;
import com.example.DumbleSubscription.domain.enums.SubscriptionStatus;
import com.example.DumbleSubscription.dto.EntryTokenResponse;
import com.example.DumbleSubscription.dto.ScanRequest;
import com.example.DumbleSubscription.dto.ScanResponse;
import com.example.DumbleSubscription.exception.BusinessRuleViolationException;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.BundleSubscriptionRepository;
import com.example.DumbleSubscription.repository.EntryLogRepository;
import com.example.DumbleSubscription.repository.EntryTokenRepository;
import com.example.DumbleSubscription.repository.ParticipantGymNoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Per Subscription PDF Section 21 — gym entry QR codes.
 *
 * Token lifecycle: fresh on every screen-open (Decision 21.2). Single-use,
 * 5-minute TTL. Generating a new token supersedes any prior unused token
 * for the same BundleSubscription.
 */
@Service
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;
    private final EntryLogRepository entryLogRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final ParticipantGymNoteRepository noteRepository;
    private final SellerLifecycleService sellerLifecycleService;
    private final ObjectMapper objectMapper;
    private final long ttlMinutes;
    private final SecureRandom random = new SecureRandom();

    public EntryTokenService(EntryTokenRepository entryTokenRepository,
                             EntryLogRepository entryLogRepository,
                             BundleSubscriptionRepository bundleSubscriptionRepository,
                             ParticipantGymNoteRepository noteRepository,
                             SellerLifecycleService sellerLifecycleService,
                             ObjectMapper objectMapper,
                             @Value("${subscription.entry-token.ttl-minutes:5}") long ttlMinutes) {
        this.entryTokenRepository = entryTokenRepository;
        this.entryLogRepository = entryLogRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.noteRepository = noteRepository;
        this.sellerLifecycleService = sellerLifecycleService;
        this.objectMapper = objectMapper;
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public EntryTokenResponse generate(UUID participantId, UUID bundleSubscriptionId) {
        BundleSubscription sub = bundleSubscriptionRepository.findById(bundleSubscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        if (!sub.getParticipantId().equals(participantId)) {
            throw new ResourceNotFoundException("Subscription not found");
        }
        // Decision 21.1 — gym bundles only.
        if (sub.getSellerType() != SellerType.GYM) {
            throw new BusinessRuleViolationException("Entry tokens are issued only for gym bundles");
        }
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Subscription is not active");
        }
        // Block token generation when the gym is suspended/banned — saves the
        // participant a wasted scan and gives a clear up-front error.
        if (!sellerLifecycleService.canFirePayouts(sub.getSellerId())) {
            throw new BusinessRuleViolationException("This gym is currently suspended");
        }

        // Supersede any existing ACTIVE token for this sub.
        List<EntryToken> existing = entryTokenRepository
                .findByBundleSubscriptionIdAndStatus(bundleSubscriptionId, EntryTokenStatus.ACTIVE);
        Instant now = Instant.now();
        for (EntryToken old : existing) {
            old.setStatus(EntryTokenStatus.SUPERSEDED);
        }

        EntryToken token = new EntryToken();
        token.setBundleSubscriptionId(sub.getId());
        token.setParticipantId(participantId);
        token.setGymId(sub.getSellerId());
        token.setTokenSecret(generateSecret());
        token.setGeneratedAt(now);
        token.setExpiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES));
        token.setStatus(EntryTokenStatus.ACTIVE);
        entryTokenRepository.save(token);

        return EntryTokenResponse.builder()
                .qrPayload(token.getTokenSecret())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    @Transactional
    public ScanResponse scan(UUID staffUserId, ScanRequest req) {
        Instant now = Instant.now();
        EntryToken token = entryTokenRepository.findByTokenSecret(req.getQrPayload()).orElse(null);

        if (token == null) {
            return logAndReturn(null, req.getGymId(), null, staffUserId,
                    EntryResult.DENIED, EntryDenialReason.TOKEN_INVALID, null);
        }
        if (token.getStatus() == EntryTokenStatus.USED) {
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.TOKEN_USED, null);
        }
        if (token.getStatus() != EntryTokenStatus.ACTIVE || token.getExpiresAt().isBefore(now)) {
            // Lazily mark expired
            token.setStatus(EntryTokenStatus.EXPIRED);
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.TOKEN_EXPIRED, null);
        }
        if (!token.getGymId().equals(req.getGymId())) {
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.WRONG_GYM, null);
        }

        BundleSubscription sub = bundleSubscriptionRepository.findById(token.getBundleSubscriptionId())
                .orElse(null);
        if (sub == null) {
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.NO_SUBSCRIPTION, null);
        }
        if (sub.getStartedAt() != null && sub.getStartedAt().isAfter(now)) {
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.NOT_STARTED, sub);
        }
        if (sub.getEndsAt() != null && sub.getEndsAt().isBefore(now)) {
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, EntryDenialReason.EXPIRED, sub);
        }
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            EntryDenialReason reason = sub.getStatus() == SubscriptionStatus.REFUNDED
                    ? EntryDenialReason.BANNED
                    : EntryDenialReason.SUSPENDED;
            return logAndReturn(token.getId(), req.getGymId(), token.getParticipantId(), staffUserId,
                    EntryResult.DENIED, reason, sub);
        }

        // Granted — consume token, log, return participant + bundle info + notes.
        token.setStatus(EntryTokenStatus.USED);
        token.setUsedAt(now);

        List<ParticipantGymNote> notes = noteRepository
                .findByGymIdAndParticipantIdOrderByCreatedAtDesc(req.getGymId(), token.getParticipantId());
        List<String> noteTexts = notes.stream().map(ParticipantGymNote::getNote).toList();

        return logAndReturnGranted(token, sub, staffUserId, noteTexts);
    }

    private ScanResponse logAndReturn(UUID tokenId, UUID gymId, UUID participantId, UUID staffUserId,
                                      EntryResult result, EntryDenialReason reason, BundleSubscription sub) {
        EntryLog log = new EntryLog();
        log.setEntryTokenId(tokenId);
        log.setGymId(gymId);
        log.setParticipantId(participantId);
        log.setStaffUserId(staffUserId);
        log.setResult(result);
        log.setDenialReason(reason);
        log.setScannedAt(Instant.now());
        entryLogRepository.save(log);

        ScanResponse response = new ScanResponse();
        response.setResult(result.name());
        response.setDenialReason(reason == null ? null : reason.name());
        if (sub != null) {
            response.setBundleName(sub.getBundleName());
            response.setStartDate(sub.getStartedAt());
            response.setEndDate(sub.getEndsAt());
        }
        return response;
    }

    private ScanResponse logAndReturnGranted(EntryToken token, BundleSubscription sub,
                                             UUID staffUserId, List<String> notes) {
        EntryLog log = new EntryLog();
        log.setEntryTokenId(token.getId());
        log.setGymId(token.getGymId());
        log.setParticipantId(token.getParticipantId());
        log.setStaffUserId(staffUserId);
        log.setResult(EntryResult.GRANTED);
        log.setScannedAt(Instant.now());
        entryLogRepository.save(log);

        long endsInDays = sub.getEndsAt() == null ? -1
                : Math.max(0, ChronoUnit.DAYS.between(Instant.now(), sub.getEndsAt()));

        ScanResponse response = new ScanResponse();
        response.setResult(EntryResult.GRANTED.name());
        response.setParticipantId(token.getParticipantId());
        response.setBundleName(sub.getBundleName());
        response.setAmenities(deserializeAmenities(sub.getAmenitiesJson()));
        response.setStartDate(sub.getStartedAt());
        response.setEndDate(sub.getEndsAt());
        response.setEndsInDays(endsInDays);
        response.setNotes(notes);
        return response;
    }

    private List<String> deserializeAmenities(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
