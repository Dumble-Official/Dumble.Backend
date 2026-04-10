package com.dumble.service.gym.service.impl;

import com.dumble.service.gym.domain.dto.GymImageResponse;
import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.entity.GymImage;
import com.dumble.service.gym.domain.enumuration.GymImageType;
import com.dumble.service.gym.domain.mapper.GymImageMapper;
import com.dumble.service.gym.exception.ResourceNotFoundException;
import com.dumble.service.gym.repository.GymImageRepository;
import com.dumble.service.gym.repository.GymRepository;
import com.dumble.service.gym.service.CloudinaryService;
import com.dumble.service.gym.service.GymImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GymImageServiceImpl implements GymImageService {

    private final GymImageRepository gymImageRepository;
    private final GymRepository gymRepository;
    private final GymImageMapper gymImageMapper;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public GymImageResponse uploadImage(UUID gymId, MultipartFile file, GymImageType type) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new ResourceNotFoundException("Gym not found with id: " + gymId));

        Map<String, String> uploadData = cloudinaryService.uploadImage(file);
        GymImage gymImage = new GymImage();
        gymImage.setGym(gym);
        gymImage.setType(type);
        gymImage.setUrl(uploadData.get("url"));
        gymImage.setPublicId(uploadData.get("publicId"));

        return gymImageMapper.toResponse(gymImageRepository.save(gymImage));
    }

    @Override
    @Transactional
    public List<GymImageResponse> uploadMultipleImages(UUID gymId, List<MultipartFile> files) {
        return files.stream()
                .map(file -> uploadImage(gymId, file, GymImageType.NORMAL))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymImageResponse> getGymImagesByType(UUID gymId, GymImageType type) {
        return gymImageRepository.findByGymIdAndType(gymId, type)
                .stream()
                .map(gymImageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GymImageResponse replaceImage(UUID gymId, MultipartFile file, GymImageType type) {
        List<GymImage> oldGymImages = gymImageRepository.findByGymIdAndType(gymId, type);

        for (GymImage oldImage : oldGymImages) {
            if (oldImage.getPublicId() != null) {
                cloudinaryService.deleteFile(oldImage.getPublicId());
            }
            gymImageRepository.delete(oldImage);
        }

        return uploadImage(gymId, file, type);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GymImageResponse> getGymImages(UUID gymId) {
        return gymImageRepository.findByGymId(gymId)
                .stream()
                .map(gymImageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {
        GymImage gymImage = gymImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));
        cloudinaryService.deleteFile(gymImage.getPublicId());
        gymImageRepository.delete(gymImage);
    }
}
