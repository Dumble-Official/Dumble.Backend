package com.example.DumblePayment.domain.enums;

/**
 * Decision 2.3 — wallet methods (Vodafone Cash, Etisalat Cash, Orange
 * Money, We Pay) cannot be silently re-charged for renewals. Subscription's
 * renewal path branches on this; Payment just records what was used.
 */
public enum PaymentMethodKind {
    CARD,
    WALLET,
    OTHER
}
