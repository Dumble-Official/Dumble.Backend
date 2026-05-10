package com.example.DumbleWallet.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CurrentUser {
    private UUID id;
    private String email;
    private String displayName;
    private String userType;        // PARTICIPANT | TRAINER | GYM | GYM_OWNER | ADMIN
    private List<String> roles;
}
