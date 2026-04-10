package com.dumble.service.gym.service;

import com.dumble.service.gym.domain.dto.GymDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface GymDocumentService {

    GymDocumentResponse uploadDocument(MultipartFile file, UUID gymId);

    List<GymDocumentResponse> uploadMultipleDocuments(List<MultipartFile> files, UUID gymId);

    List<GymDocumentResponse> getGymDocuments(UUID gymId);

    void deleteDocument(Long documentId);
}
