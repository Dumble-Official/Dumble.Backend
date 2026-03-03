package com.dumble.service.gym.domain.mapper;

import com.dumble.service.gym.domain.dto.GymDocumentResponse;
import com.dumble.service.gym.domain.entity.GymDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GymDocumentMapper {
    GymDocumentResponse toResponse(GymDocument document);
}
