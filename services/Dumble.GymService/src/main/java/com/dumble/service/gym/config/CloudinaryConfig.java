package com.dumble.service.gym.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${CLOUDINARY_URL:cloudinary://placeholder:placeholder@placeholder}") String url) {
        return new Cloudinary(url);
    }
}
