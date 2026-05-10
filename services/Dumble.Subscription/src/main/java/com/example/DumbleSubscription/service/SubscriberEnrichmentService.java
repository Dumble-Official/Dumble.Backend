package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.AuthenticationClient;
import com.example.DumbleSubscription.client.dto.UserPublicProfile;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.ParticipantPreferences;
import com.example.DumbleSubscription.dto.SubscriberView;
import com.example.DumbleSubscription.repository.ParticipantPreferencesRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches BundleSubscription rows into the public-facing SubscriberView the
 * gym dashboard renders. Consults Authentication for display name + photo
 * (Decision 10.2), participant preferences for opt-out (10.1), and the
 * IsDeleted flag for soft-deletes (10.4).
 *
 * Failures from Authentication degrade gracefully — a row is still returned,
 * just without the enriched fields.
 *
 * merged_bug_005 — enrichment is now batch: a single fanned-out call to
 * Authentication and a single findAllById on preferences, not N round-trips
 * per row. A 1000-subscriber gym used to issue 2000 sequential blocking
 * calls on the request thread.
 */
@Service
public class SubscriberEnrichmentService {

    private final AuthenticationClient authenticationClient;
    private final ParticipantPreferencesRepository preferencesRepository;

    public SubscriberEnrichmentService(AuthenticationClient authenticationClient,
                                       ParticipantPreferencesRepository preferencesRepository) {
        this.authenticationClient = authenticationClient;
        this.preferencesRepository = preferencesRepository;
    }

    public List<SubscriberView> enrich(List<BundleSubscription> subs) {
        if (subs.isEmpty()) return List.of();

        Set<UUID> participantIds = subs.stream()
                .map(BundleSubscription::getParticipantId)
                .collect(Collectors.toUnmodifiableSet());

        // 1 batched HTTP fanout (concurrency-bounded) instead of N sequential
        // blocking calls.
        Map<UUID, UserPublicProfile> profiles =
                authenticationClient.getPublicProfiles(participantIds);
        if (profiles == null) profiles = Map.of();

        // 1 SQL roundtrip instead of N findById calls.
        Map<UUID, ParticipantPreferences> prefs = preferencesRepository.findAllById(participantIds).stream()
                .collect(Collectors.toUnmodifiableMap(ParticipantPreferences::getParticipantId, p -> p));

        Map<UUID, UserPublicProfile> finalProfiles = profiles;
        return subs.stream()
                .map(sub -> toView(sub, finalProfiles.get(sub.getParticipantId()), prefs.get(sub.getParticipantId())))
                .toList();
    }

    private SubscriberView toView(BundleSubscription sub, UserPublicProfile profile, ParticipantPreferences pref) {
        boolean deleted = profile != null && profile.isDeleted();
        boolean hidden = pref != null && pref.isHideFromGymLists();

        return SubscriberView.builder()
                .participantId(sub.getParticipantId())
                .displayName(deleted ? "[deleted account]"
                        : hidden ? "[hidden]"
                        : profile == null ? null : profile.getDisplayName())
                .profileImageUrl(deleted || hidden || profile == null ? null : profile.getProfileImageUrl())
                .bundleName(sub.getBundleName())
                .activeSince(sub.getStartedAt())
                .endsAt(sub.getEndsAt())
                .deleted(deleted)
                .hidden(hidden)
                .build();
    }
}
