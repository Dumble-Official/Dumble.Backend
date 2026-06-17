package com.dumble.service.session.domain.mapper;

import com.dumble.service.session.domain.dto.request.SessionCreateRequest;
import com.dumble.service.session.domain.dto.request.SessionUpdateRequest;
import com.dumble.service.session.domain.dto.response.SessionResponse;
import com.dumble.service.session.domain.entity.Session;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SessionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentParticipants", ignore = true, defaultValue = "0")
    @Mapping(target = "status", ignore = true, defaultValue = "DRAFT")
    @Mapping(target = "createdAt", ignore = true)  // Handled by @CreationTimestamp
    Session toEntity(SessionCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerType", ignore = true)
    @Mapping(target = "gymId", ignore = true)
    @Mapping(target = "trainerId", ignore = true)
    @Mapping(target = "currentParticipants", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(SessionUpdateRequest request, @MappingTarget Session session);

    @Mapping(target = "availableSpots", expression = "java(calculateAvailableSpots(session))")
    SessionResponse toResponse(Session session);

    default Integer calculateAvailableSpots(Session session) {
        if (session == null || session.getMaxCapacity() == null || session.getCurrentParticipants() == null) {
            return 0;
        }
        return Math.max(0, session.getMaxCapacity() - session.getCurrentParticipants());
    }

}
