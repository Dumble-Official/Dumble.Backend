package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.enumuration.GenderType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

@Setter
@Getter
public class GymCreateRequest {
    private String name;
    private String bio;
    private String address;

    private LocationDto location;
    private GenderType genderType;

    private String email;
    private String phone;
    private String licenseId;

    private LocalTime openTime;
    private LocalTime closeTime;

    private List<Long> amenityIds;
}
