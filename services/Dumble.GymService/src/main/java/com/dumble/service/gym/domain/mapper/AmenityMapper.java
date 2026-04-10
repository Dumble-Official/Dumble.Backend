package com.dumble.service.gym.domain.mapper;

import com.dumble.service.gym.domain.dto.AmenityDto;
import com.dumble.service.gym.domain.entity.Amenity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AmenityMapper {

    AmenityDto toDto(Amenity amenity);

    @Mapping(target = "gyms", ignore = true)
    Amenity toEntity(AmenityDto dto);
}
