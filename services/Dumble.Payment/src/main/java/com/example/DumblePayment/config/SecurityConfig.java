package com.example.DumblePayment.config;

import com.example.DumblePayment.security.SystemAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SystemAuthenticationFilter systemAuthenticationFilter;

    public SecurityConfig(SystemAuthenticationFilter systemAuthenticationFilter) {
        this.systemAuthenticationFilter = systemAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Operational
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Paymob webhooks — Decision 4.1: HMAC-verified inside the controller,
                        // exempt from system JWT (Paymob doesn't carry our keys).
                        .requestMatchers("/payment/webhooks/paymob").permitAll()
                        // Admin observability surface — must require ADMIN, not just
                        // any system token. Without this, every sibling service
                        // (Subscription, Wallet, …) with a SERVICE JWT can read the
                        // recon dashboard's failure patterns and run windows.
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Everything else (including /payment/payment-methods/tokenize)
                        // needs ROLE_SERVICE — frontends must call through a gateway
                        // that mints a system JWT, so the userId/token binding can't
                        // be forged by an unauthenticated caller.
                        .anyRequest().hasRole("SERVICE")
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Unauthorized"))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, "Forbidden"))
                )
                .addFilterBefore(systemAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void writeJson(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
