package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
public class GymResponse {

        private UUID id;
        private String name;
        private String bio;
        private String address;

        private LocationDto location;

        private GymImagesDto images;

        private GenderType genderType;
        private GymStatus status;
        private boolean verified;

        private LocalTime openTime;
        private LocalTime closeTime;

        private List<AmenityDto> amenities;
        private List<StaffResponse> staff;

        private LocalDateTime createdAt;

}
