package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.domain.dto.GymDocumentResponse;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.entity.GymDocument;
import com.dumble.service.gym.domain.mapper.GymDocumentMapper;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.repository.GymDocumentRepository;
import com.dumble.service.gym.repository.GymRepository;
import com.dumble.service.gym.service.CloudinaryService;
import com.dumble.service.gym.service.GymDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GymDocumentServiceImpl implements GymDocumentService {
    private final GymDocumentRepository gymDocumentRepository;
    private final GymRepository gymRepository;
    private final CloudinaryService cloudinaryService;
    private final GymDocumentMapper gymDocumentMapper;

    @Override
    @Transactional
    public GymDocumentResponse uploadDocument(MultipartFile file, UUID gymId) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        String url = cloudinaryService.uploadFile(file);
        GymDocument gymDocument = new GymDocument();
        gymDocument.setGym(gym);
        gymDocument.setDocumentUrl(url);

        return gymDocumentMapper.toResponse(gymDocumentRepository.save(gymDocument));
    }

    @Override
    @Transactional
    public List<GymDocumentResponse> uploadMultipleDocuments(List<MultipartFile> files, UUID gymId) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        return files.stream()
                .map(file -> {
                    String url = cloudinaryService.uploadFile(file);
                    GymDocument gymDocument = new GymDocument();
                    gymDocument.setGym(gym);
                    gymDocument.setDocumentUrl(url);
                    return gymDocumentMapper.toResponse(gymDocumentRepository.save(gymDocument));
                }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymDocumentResponse> getGymDocuments(UUID gymId) {
        return gymDocumentRepository.findByGymId(gymId).stream()
                .map(gymDocumentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        GymDocument document = gymDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
        gymDocumentRepository.delete(document);
    }
}
