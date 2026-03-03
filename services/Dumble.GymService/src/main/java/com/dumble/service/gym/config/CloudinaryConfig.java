package com.dumble.service.gym.config;

import com.cloudinary.Cloudinary;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(Dotenv.load().get("CLOUDINARY_URL", System.getenv("CLOUDINARY_URL")));
    }
}

