package com.example.DumbleAuthentication.controller;

import com.example.DumbleAuthentication.dto.response.UserResponse;
import com.example.DumbleAuthentication.service.BanService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class BanController {

    private final BanService banService;

    public BanController(BanService banService) {
        this.banService = banService;
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<Map<String, String>> banUser(@PathVariable Long id) {
        banService.banUser(id);
        return ResponseEntity.ok(Map.of("message", "User banned successfully"));
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<Map<String, String>> unbanUser(@PathVariable Long id) {
        banService.unbanUser(id);
        return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
    }

    @GetMapping("/banned")
    public ResponseEntity<List<UserResponse>> getBannedUsers() {
        List<UserResponse> users = banService.getBannedUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }
}
