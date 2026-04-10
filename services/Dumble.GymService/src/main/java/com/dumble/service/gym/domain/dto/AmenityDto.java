package com.dumble.service.gym.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class AmenityDto {
    private Long id;

    @NotBlank(message = "name of amenity is required")
    private String name;
    private String description;
    private Boolean isActive;
}
