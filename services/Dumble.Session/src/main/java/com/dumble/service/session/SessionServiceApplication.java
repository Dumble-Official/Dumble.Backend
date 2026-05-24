package com.dumble.service.session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

//@SpringBootApplication
@EnableFeignClients(basePackages = "com.dumble.service.session.client")
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class SessionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionServiceApplication.class, args);
    }

}
