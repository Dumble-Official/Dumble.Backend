package com.example.DumbleAuthentication.domain;

/**
 * Account roles. Every new user starts as PARTICIPANT (see AuthService.register).
 * Anything else requires admin approval (e.g. cert verification for TRAINER,
 * business-license verification for GYM_OWNER).
 *
 * GYM_OWNER is the human person who owns one or more gyms.
 * GYM is the gym page/account itself, separate from its owner.
 */
public enum UserType {
    PARTICIPANT,
    MODERATOR,
    TRAINER,
    GYM_OWNER,
    GYM,
    ADMIN
}
