package com.dumble.service.gym.client;

import com.dumble.service.gym.domain.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "DumbleAuthentication", url = "http://localhost:8081")
public interface UserClient {

    @GetMapping("/api/users/me")
    UserResponse getCurrentUser(@RequestHeader("Authorization") String token);
}
