package com.dumble.service.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumbleScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DumbleScheduleApplication.class, args);
    }
}
