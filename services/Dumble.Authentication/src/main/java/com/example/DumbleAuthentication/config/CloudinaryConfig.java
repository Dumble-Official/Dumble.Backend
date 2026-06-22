package com.example.DumbleAuthentication.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudinary client, configured from the {@code CLOUDINARY_URL} environment
 * variable (format: {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}).
 * Used to upload avatars and trainer certificates server-side so no Cloudinary
 * credential ever ships to the client.
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${CLOUDINARY_URL:cloudinary://placeholder:placeholder@placeholder}") String url) {
        return new Cloudinary(url);
    }
}
