package com.example.DumbleSubscription.client;

import com.example.DumbleSubscription.client.dto.BundleSnapshot;
import com.example.DumbleSubscription.client.dto.QuotaResponse;
import com.example.DumbleSubscription.security.SystemTokenSigner;
import com.example.DumbleSubscription.security.UserTokenForwarder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
public class BundleManagementClient {

    private final WebClient client;
    private final SystemTokenSigner signer;
    private final UserTokenForwarder userTokenForwarder;

    public BundleManagementClient(@Qualifier("bundleManagementWebClient") WebClient client,
                                  SystemTokenSigner signer,
                                  UserTokenForwarder userTokenForwarder) {
        this.client = client;
        this.signer = signer;
        this.userTokenForwarder = userTokenForwarder;
    }

    /** User-context: read bundle data at checkout to snapshot onto BundleSubscription. */
    public BundleSnapshot getBundle(UUID bundleId) {
        return client.get()
                .uri("/api/bundles/{id}", bundleId)
                .header("Authorization", authHeader())
                .retrieve()
                .bodyToMono(BundleSnapshot.class)
                .block();
    }

    /** User-context: ask BundleManagement for current quota state of a seller. */
    public QuotaResponse getQuota(UUID sellerId) {
        return client.get()
                .uri("/api/sellers/{id}/quota", sellerId)
                .header("Authorization", authHeader())
                .retrieve()
                .bodyToMono(QuotaResponse.class)
                .block();
    }

    private String authHeader() {
        String forwarded = userTokenForwarder.currentBearer();
        return forwarded != null ? forwarded : "Bearer " + signer.mint("bundle-management");
    }
}
