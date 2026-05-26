package com.dumble.service.session.config;

import com.dumble.service.session.security.SystemTokenMinter;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * Session -> Payment is system-to-system, not user-context. Forwarding the
 * inbound user JWT (the original behaviour) had two problems:
 *
 *  1. Wrong {@code aud}: the inbound token carries {@code aud=dumble-app}
 *     (or the gateway-issued value), not {@code aud=payment}. Payment's
 *     SystemTokenVerifier rejects it with 401.
 *  2. RabbitListener-thread callers (e.g. async webhook handlers triggering
 *     refunds) have no inbound request context — RequestContextHolder
 *     returns null and the call goes unauthenticated.
 *
 * Mint a fresh short-lived system JWT per outbound call instead.
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor paymentSystemAuthInterceptor(SystemTokenMinter minter) {
        return requestTemplate -> {
            String token = minter.generateSystemToken("payment");
            requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        };
    }
}