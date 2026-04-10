package com.dumble.service.gym.domain.mapper;

import com.dumble.service.gym.domain.dto.AddGymStaffRequest;
import com.dumble.service.gym.domain.dto.StaffResponse;
import com.dumble.service.gym.domain.entity.GymStaff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffMapper {

    StaffResponse toResponse(GymStaff staff);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "gym", ignore = true)
    GymStaff toEntity(AddGymStaffRequest request);
}
