package com.example.DumbleSubscription.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated user attached to {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}.
 * Populated by {@link com.example.DumbleSubscription.security.JwtAuthenticationFilter}.
 */
@Data
public class CurrentUser {
    private UUID id;
    private String email;
    private String displayName;
    /** PARTICIPANT | TRAINER | GYM_OWNER | GYM | ADMIN */
    private String userType;
    private List<String> roles;
}
