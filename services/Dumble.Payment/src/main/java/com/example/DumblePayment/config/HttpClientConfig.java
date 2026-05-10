package com.example.DumblePayment.config;

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
 * One pre-built WebClient — the Paymob client. Shared retry/timeout policy
 * with the rest of the platform: 3s connect, 10s read/write, 3 retries with
 * exponential backoff on 5xx + connection failures.
 */
@Configuration
public class HttpClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_WRITE_TIMEOUT_S = 10;

    @Bean(name = "paymobClient")
    public WebClient paymobClient(@Value("${paymob.base-url}") String baseUrl) {
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
