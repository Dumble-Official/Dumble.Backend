package com.example.DumbleSubscription.service;

import com.example.DumbleSubscription.client.AuthenticationClient;
import com.example.DumbleSubscription.client.dto.UserPublicProfile;
import com.example.DumbleSubscription.domain.BundleSubscription;
import com.example.DumbleSubscription.domain.ParticipantPreferences;
import com.example.DumbleSubscription.dto.SubscriberView;
import com.example.DumbleSubscription.repository.ParticipantPreferencesRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Enriches BundleSubscription rows into the public-facing SubscriberView the
 * gym dashboard renders. Consults Authentication for display name + photo
 * (Decision 10.2), participant preferences for opt-out (10.1), and the
 * IsDeleted flag for soft-deletes (10.4).
 *
 * Failures from Authentication degrade gracefully — a row is still returned,
 * just without the enriched fields.
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
        return subs.stream().map(this::enrichOne).toList();
    }

    private SubscriberView enrichOne(BundleSubscription sub) {
        UUID id = sub.getParticipantId();
        UserPublicProfile profile = authenticationClient.getPublicProfile(id);
        ParticipantPreferences prefs = preferencesRepository.findById(id).orElse(null);

        boolean deleted = profile != null && profile.isDeleted();
        boolean hidden = prefs != null && prefs.isHideFromGymLists();

        return SubscriberView.builder()
                .participantId(id)
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
