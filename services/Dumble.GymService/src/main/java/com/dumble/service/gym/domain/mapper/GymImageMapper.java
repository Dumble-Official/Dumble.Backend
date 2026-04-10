package com.dumble.service.gym.domain.mapper;

import com.dumble.service.gym.domain.dto.GymImageResponse;
import com.dumble.service.gym.domain.entity.GymImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GymImageMapper {

    @Mapping(target = "imageType", source = "type")
    GymImageResponse toResponse(GymImage image);
}
