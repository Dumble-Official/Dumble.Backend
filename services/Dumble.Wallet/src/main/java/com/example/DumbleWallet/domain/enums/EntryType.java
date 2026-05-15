package com.example.DumbleWallet.domain.enums;

/**
 * Direction of a {@code wallet_entries} row. Always positive {@code amountCents};
 * sign is implied by the type.
 */
public enum EntryType {
    CREDIT,
    DEBIT
}
