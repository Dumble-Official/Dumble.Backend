package com.dumble.service.session.domain.dto.response;

import com.dumble.service.session.domain.enumuration.OwnerType;
import com.dumble.service.session.domain.enumuration.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SessionResponse {
    private UUID id;
    private OwnerType ownerType;
    private UUID gymId;
    private UUID trainerId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxCapacity;
    private Integer currentParticipants;
    private Integer availableSpots;  // Calculated: maxCapacity - currentParticipants
    private BigDecimal price;
    private SessionStatus status;
    private LocalDateTime createdAt;
}
