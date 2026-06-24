package com.example.DumbleAuthentication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/google")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        // Service-to-service endpoints — gated by a shared secret in the controller, not a JWT.
                        .requestMatchers("/api/internal/**").permitAll()
                        // Ban management — ADMIN and MODERATOR only
                        .requestMatchers(HttpMethod.POST, "/api/users/*/ban", "/api/users/*/unban")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/users/banned")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        // Role-promotion review — ADMIN only.
                        .requestMatchers("/api/admin/role-requests/**")
                        .hasRole("ADMIN")
                        // Authenticated endpoints
                        .requestMatchers("/api/auth/logout", "/api/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me/**").authenticated()
                        // Public people-search + public per-user summary — any logged-in user.
                        // Declared BEFORE the admin /api/users/* rule so they aren't gated to staff.
                        .requestMatchers(HttpMethod.GET, "/api/users/search").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/summary").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").authenticated()
                        // Admin/Moderator user search + lookup (declared AFTER /me so self stays open).
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
