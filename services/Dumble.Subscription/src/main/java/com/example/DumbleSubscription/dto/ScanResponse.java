package com.example.DumbleSubscription.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ScanResponse {
    private String result;          // GRANTED | DENIED
    private String denialReason;    // present when DENIED
    private UUID participantId;
    private String bundleName;
    /** Amenities / permissions included with this subscription (Decision 21.4). */
    private List<String> amenities;
    private Instant startDate;
    private Instant endDate;
    private long endsInDays;
    private List<String> notes;
}
