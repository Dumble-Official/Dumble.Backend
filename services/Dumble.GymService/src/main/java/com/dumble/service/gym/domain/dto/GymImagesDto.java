package com.dumble.service.gym.domain.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class GymImagesDto {
    private String profile;
    private String cover;
    private List<String> normal = new ArrayList<>();
}
