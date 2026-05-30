package com.example.DumbleAuthentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumbleAuthenticationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DumbleAuthenticationApplication.class, args);
    }

}

