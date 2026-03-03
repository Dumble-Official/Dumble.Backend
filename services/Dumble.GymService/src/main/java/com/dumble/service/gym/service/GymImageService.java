package com.dumble.service.gym.service;


import com.dumble.service.gym.domain.dto.GymImageResponse;
import com.dumble.service.gym.domain.enumuration.GymImageType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface GymImageService {
    GymImageResponse uploadImage(UUID gymId, MultipartFile file, GymImageType type);

    List<GymImageResponse> uploadMultipleImages(UUID gymId, List<MultipartFile> files);

    List<GymImageResponse> getGymImagesByType(UUID gymId, GymImageType type);

    GymImageResponse replaceImage(UUID gymId, MultipartFile file, GymImageType type);

    List<GymImageResponse> getGymImages(UUID gymId);

    void deleteImage(Long imageId);
}
