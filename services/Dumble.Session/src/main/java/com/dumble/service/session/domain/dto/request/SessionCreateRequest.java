package com.dumble.service.session.domain.dto.request;


import com.dumble.service.session.domain.enumuration.OwnerType;
import jakarta.validation.constraints.*;
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
public class SessionCreateRequest {
    @NotNull(message = "Owner type is required")
    private OwnerType ownerType;

    private UUID gymId;

    private UUID trainerId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    private String description;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    private LocalDateTime endTime;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer maxCapacity;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal price;

    /** Cross-field: endTime must be strictly after startTime. */
    @AssertTrue(message = "End time must be after start time")
    public boolean isEndAfterStart() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    /**
     * Cross-field: the owner-type tag must match which id is set.
     * Note: SessionController derives the actual gymId/trainerId from the
     * caller's JWT (so callers can't impersonate other gyms/trainers); this
     * validator just keeps the body internally consistent.
     */
    @AssertTrue(message = "Owner identifiers must match ownerType")
    public boolean isOwnerIdsConsistent() {
        if (ownerType == null) return true;
        return switch (ownerType) {
            case GYM -> trainerId == null;
            case TRAINER -> gymId == null;
        };
    }
}
