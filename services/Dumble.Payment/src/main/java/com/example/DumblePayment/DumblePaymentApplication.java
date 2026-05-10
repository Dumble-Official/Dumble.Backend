package com.example.DumblePayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumblePaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DumblePaymentApplication.class, args);
    }
}
