package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.GymImageResponse;
import com.dumble.service.gym.domain.enumuration.GymImageType;
import com.dumble.service.gym.service.GymImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gyms/{gymId}/images")
@Tag(name = "gym image")
public class GymImageController {
    private final GymImageService gymImageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GymImageResponse> uploadImage(@PathVariable UUID gymId, @RequestPart("file") MultipartFile file, @RequestParam("type") GymImageType type) {
        return new ResponseEntity<>(gymImageService.uploadImage(gymId, file, type), HttpStatus.CREATED);
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<GymImageResponse>> uploadMultipleImages(@PathVariable UUID gymId, @RequestPart("files") List<MultipartFile> files){
        return new ResponseEntity<>(gymImageService.uploadMultipleImages(gymId, files), HttpStatus.CREATED);
    }

    @GetMapping("images-by-type")
    public ResponseEntity<List<GymImageResponse>> getGymImagesByType(@PathVariable UUID gymId, @RequestParam("type") GymImageType type) {
        return new ResponseEntity<>(gymImageService.getGymImagesByType(gymId, type), HttpStatus.OK);
    }

    @PostMapping(value = "/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GymImageResponse> replaceImage(@PathVariable UUID gymId, @RequestPart("file") MultipartFile file, @RequestParam("type") GymImageType type) {
        return new ResponseEntity<>(gymImageService.replaceImage(gymId, file, type), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<List<GymImageResponse>> getGymImages(@PathVariable UUID gymId) {
        return new ResponseEntity<>(gymImageService.getGymImages(gymId), HttpStatus.OK);
    }

    @DeleteMapping("/delete/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID gymId, @PathVariable Long imageId) {
        gymImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}
