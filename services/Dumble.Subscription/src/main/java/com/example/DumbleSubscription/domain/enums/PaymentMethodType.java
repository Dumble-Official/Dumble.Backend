package com.example.DumbleSubscription.domain.enums;

/**
 * Per Subscription PDF Decision 7.2 — auto-renewal is supported only for cards.
 * Wallet payments (Vodafone Cash, Etisalat Cash, etc.) cannot be silently
 * recharged — they need an explicit user-initiated authorization.
 */
public enum PaymentMethodType {
    CARD,
    WALLET,
    OTHER
}
