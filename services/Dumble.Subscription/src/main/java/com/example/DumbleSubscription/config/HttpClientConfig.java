package com.example.DumbleSubscription.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Pre-built WebClient beans for each sibling service we call.
 *
 * Every WebClient has:
 *   - 3-second connect timeout, 10-second read/write timeout (production hangs
 *     on Payment / Wallet would otherwise tie up calling threads forever)
 *   - automatic retry with backoff on 5xx + connection failures (3 attempts,
 *     exponential backoff starting at 200ms — handles transient blips without
 *     bouncing back to the caller)
 */
@Configuration
public class HttpClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_WRITE_TIMEOUT_S = 10;

    @Bean(name = "paymentClient")
    public WebClient paymentClient(@Value("${services.payment.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    @Bean(name = "walletClient")
    public WebClient walletClient(@Value("${services.wallet.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    // Named "...WebClient" deliberately so this bean doesn't collide with the
    // @Component-derived bean from BundleManagementClient.class (which Spring
    // names "bundleManagementClient" from the class name). Spring Boot 3.x
    // refuses bean-definition overrides by default; clashing names crash boot.
    @Bean(name = "bundleManagementWebClient")
    public WebClient bundleManagementWebClient(@Value("${services.bundle-management.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    @Bean(name = "gymClient")
    public WebClient gymClient(@Value("${services.gym.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    // Same rationale as bundleManagementWebClient — avoid collision with the
    // @Component-derived "authenticationClient" bean from AuthenticationClient.
    @Bean(name = "authenticationWebClient")
    public WebClient authenticationWebClient(@Value("${services.authentication.base-url}") String baseUrl) {
        return build(baseUrl);
    }

    private static WebClient build(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(READ_WRITE_TIMEOUT_S))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_WRITE_TIMEOUT_S))
                        .addHandlerLast(new WriteTimeoutHandler(READ_WRITE_TIMEOUT_S)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(retryOnTransient())
                .build();
    }

    /**
     * Retries on connection failures and 5xx responses. 4xx responses are
     * propagated immediately — they signal a contract or data issue, not a
     * transient blip, and retrying will only multiply the impact.
     */
    private static ExchangeFilterFunction retryOnTransient() {
        return (request, next) -> next.exchange(request)
                .flatMap(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is5xxServerError()) {
                        return response.releaseBody()
                                .then(reactor.core.publisher.Mono.error(
                                        new TransientHttpException("HTTP " + status.value())));
                    }
                    return reactor.core.publisher.Mono.just(response);
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                        .filter(t -> t instanceof TransientHttpException
                                || t instanceof java.io.IOException
                                || t instanceof java.util.concurrent.TimeoutException));
    }

    private static class TransientHttpException extends RuntimeException {
        TransientHttpException(String msg) { super(msg); }
    }
}
