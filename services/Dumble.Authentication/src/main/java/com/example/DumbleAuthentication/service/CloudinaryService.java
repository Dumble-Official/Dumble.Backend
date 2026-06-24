package com.example.DumbleAuthentication.service;

import com.cloudinary.Cloudinary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Thin wrapper over the Cloudinary SDK for uploading user-supplied files
 * (avatars, trainer certificates). {@code resource_type: auto} handles both
 * images and PDFs. Returns the hosted {@code secure_url}.
 */
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadFile(MultipartFile file) {
        try {
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    Map.of("resource_type", "auto", "access_mode", "public"));
            return result.get("secure_url").toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to Cloudinary", e);
        }
    }
}
