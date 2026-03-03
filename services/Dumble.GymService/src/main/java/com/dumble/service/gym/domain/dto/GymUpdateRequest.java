package com.dumble.service.gym.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Setter
@Getter
public class GymUpdateRequest {

    private String name;
    private String bio;
    private String address;

    private LocationDto location;

    private String email;
    private String phone;

    private LocalTime openTime;
    private LocalTime closeTime;
}
