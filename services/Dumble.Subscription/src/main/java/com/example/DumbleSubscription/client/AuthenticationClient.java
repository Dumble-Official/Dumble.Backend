package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.UserPublicProfile;
import com.example.DumbleSubscription.security.SystemTokenSigner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AuthenticationClient {

    private final WebClient client;
    private final SystemTokenSigner signer;

    public AuthenticationClient(@Qualifier("authenticationWebClient") WebClient client,
                                SystemTokenSigner signer) {
        this.client = client;
        this.signer = signer;
    }

    /**
     * Fetches the public profile (name, photo, etc.) for a user. System-context
     * call — used by Subscription's seller dashboards to enrich subscriber lists
     * with display names and photos (no PII per Decision 10.2).
     *
     * Returns null when the upstream user is not found or has been soft-deleted
     * (Decision 10.4 — caller should render "[deleted account]" in that case).
     */
    public UserPublicProfile getPublicProfile(UUID userId) {
        try {
            return client.get()
                    .uri("/api/users/{id}/public-profile", userId)
                    .header("Authorization", "Bearer " + signer.mint("authentication"))
                    .retrieve()
                    .bodyToMono(UserPublicProfile.class)
                    .block();
        } catch (WebClientResponseException.NotFound notFound) {
            return null;
        } catch (Exception ex) {
            // Failing closed (returning null) is safer than blocking the whole
            // dashboard render on Authentication availability.
            return null;
        }
    }

    /**
     * merged_bug_005 — bulk variant. Fans the per-id calls out concurrently
     * over the WebClient connection pool so the seller dashboard's enrich
     * step is bounded by the slowest call rather than the sum of N sequential
     * blocking calls. A bulk POST endpoint on Authentication would be cleaner
     * but requires a contract change; this is a self-contained improvement.
     *
     * Concurrency cap of 16 keeps a popular-gym dashboard from saturating
     * the WebClient pool, and the per-call .onErrorResume keeps the
     * fail-closed semantics of the per-row variant (a missing or 5xx user
     * just gets dropped from the result map).
     */
    public Map<UUID, UserPublicProfile> getPublicProfiles(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        String token = signer.mint("authentication");
        return Flux.fromIterable(userIds)
                .flatMap(id -> client.get()
                        .uri("/api/users/{id}/public-profile", id)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(UserPublicProfile.class)
                        .timeout(Duration.ofSeconds(10))
                        .map(profile -> Map.entry(id, profile))
                        .onErrorResume(ex -> Mono.empty()),
                        16)
                .subscribeOn(Schedulers.boundedElastic())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
                .block();
    }
}
