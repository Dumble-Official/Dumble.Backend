package com.dumble.service.gym.domain.dto;

import com.dumble.service.gym.domain.enumuration.GymImageType;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GymImageResponse {
    private Long id;
    private String url;
    private GymImageType imageType;
}
