package com.dumble.service.gym.domain.mapper;

import com.dumble.service.gym.domain.dto.*;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.entity.GymImage;
import com.dumble.service.gym.domain.enumuration.GymImageType;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", uses = {StaffMapper.class, AmenityMapper.class})
public interface GymMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true) // Will be set in service
    @Mapping(target = "lat", source = "location.lat")
    @Mapping(target = "lng", source = "location.lng")
    @Mapping(target = "isVerified", constant = "false")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "profileImageUrl", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "amenities", ignore = true) // Will be set in service
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "gymStaff", ignore = true)
    Gym toEntity(GymCreateRequest gymCreateRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "lat", source = "location.lat")
    @Mapping(target = "lng", source = "location.lng")
    @Mapping(target = "genderType", ignore = true)
    @Mapping(target = "licenseId", ignore = true)
    @Mapping(target = "isVerified", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "profileImageUrl", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "amenities", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "gymStaff", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(GymUpdateRequest gymUpdateRequest, @MappingTarget Gym gym);

    @Mapping(target = "location", expression = "java(toLocationDto(gym))")
    @Mapping(target = "images", expression = "java(toGymImagesDto(gym.getImages()))")
    @Mapping(target = "verified", source = "isVerified")
    GymResponse toDto(Gym gym);

    default LocationDto toLocationDto(Gym gym){
        if (gym == null) return null;
        LocationDto locationDto = new LocationDto();
        locationDto.setLat(gym.getLat() != null ? gym.getLat().doubleValue() : null);
        locationDto.setLng(gym.getLng() != null ? gym.getLng().doubleValue() : null);
        return locationDto;
    }

    default GymImagesDto toGymImagesDto(List<GymImage> images){
        if (images == null){
            return new GymImagesDto();
        }
        GymImagesDto gymImagesDto = new GymImagesDto();
        for (GymImage image : images){
            if(image.getType() == GymImageType.PROFILE){
                gymImagesDto.setProfile(image.getUrl());
            }
            else if(image.getType() == GymImageType.COVER){
                gymImagesDto.setCover(image.getUrl());
            }
            else if(image.getType() == GymImageType.NORMAL){
                gymImagesDto.getNormal().add(image.getUrl());
            }
        }
        return gymImagesDto;
    }

    default BigDecimal doubleToBigDecimal(Double value){
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
