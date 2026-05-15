package com.example.DumbleWallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumbleWalletApplication {

    public static void main(String[] args) {
        SpringApplication.run(DumbleWalletApplication.class, args);
    }
}
