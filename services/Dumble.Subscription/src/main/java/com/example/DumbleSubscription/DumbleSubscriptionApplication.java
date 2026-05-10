package com.example.DumbleSubscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumbleSubscriptionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DumbleSubscriptionApplication.class, args);
    }
}
