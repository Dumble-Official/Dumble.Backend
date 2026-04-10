package com.dumble.service.gym.service;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public String uploadFile(MultipartFile file) {
        try {
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), Map.of("resource_type", "auto",
                    "access_mode", "public"));
            return uploadResult.get("secure_url").toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upload file");
        }
    }

    public Map<String, String> uploadImage(MultipartFile file) {
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), Map.of());
            return Map.of(
                    "url", uploadResult.get("secure_url").toString(),
                    "publicId", uploadResult.get("public_id").toString()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image");
        }
    }

    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, Map.of());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from Cloudinary");
        }
    }
}
