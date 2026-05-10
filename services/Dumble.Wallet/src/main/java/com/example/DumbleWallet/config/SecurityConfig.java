package com.example.DumbleWallet.config;

import com.example.DumbleWallet.security.JwtAuthenticationFilter;
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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
                        // System endpoints — auth enforced inside the controller via SystemTokenVerifier
                        // (Wallet PDF Decision 6.4 Class B). Whitelisted at Spring Security level so
                        // a user JWT isn't required.
                        .requestMatchers("/wallet/credit", "/wallet/debit").permitAll()
                        // Inter-service summary lookup (Subscription pre-checkout balance check)
                        .requestMatchers("/wallet/*/summary").permitAll()
                        // Everything else (user-facing /wallet/me/*, admin /admin/wallet/*) needs a user JWT
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Unauthorized"))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, "Forbidden"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void writeJson(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
