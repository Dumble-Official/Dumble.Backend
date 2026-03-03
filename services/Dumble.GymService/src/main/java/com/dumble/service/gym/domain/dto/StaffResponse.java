package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.enumuration.StaffRole;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class StaffResponse {
    private Long id;
    private UUID userId;
    private StaffRole role;
}
