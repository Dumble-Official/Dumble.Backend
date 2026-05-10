package com.example.DumbleWallet.dto;

import com.example.DumbleWallet.domain.WalletEntry;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WalletEntryResponse {

    private UUID id;
    private String type;
    private long amountCents;
    private String source;
    private String externalRef;
    private String memo;
    private Instant createdAt;

    public static WalletEntryResponse from(WalletEntry e) {
        return WalletEntryResponse.builder()
                .id(e.getId())
                .type(e.getType().name())
                .amountCents(e.getAmountCents())
                .source(e.getSource().name())
                .externalRef(e.getExternalRef())
                .memo(e.getMemo())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
