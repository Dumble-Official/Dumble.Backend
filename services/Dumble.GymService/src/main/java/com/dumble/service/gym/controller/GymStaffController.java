package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.AddGymStaffRequest;
import com.dumble.service.gym.domain.dto.StaffResponse;
import com.dumble.service.gym.service.GymStaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gyms/{gymId}/staff")
@Tag(name = "gym staff")
public class GymStaffController {

    private final GymStaffService gymStaffService;

    @PostMapping("/add")
    public ResponseEntity<StaffResponse> addGymStaff(@PathVariable UUID gymId, @RequestBody AddGymStaffRequest request
    , @RequestHeader("Authorization") String token) {
        return new ResponseEntity<>(gymStaffService.addGymStaff(gymId, request, token), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<StaffResponse>> getGymStaff(@PathVariable UUID gymId) {
        return new ResponseEntity<>(gymStaffService.getGymStaff(gymId), HttpStatus.OK);
    }

    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<Void> removeGymStaff(@PathVariable UUID gymId, @PathVariable UUID userId, @RequestHeader("Authorization") String token) {
        gymStaffService.removeGymStaff(gymId, userId, token);
        return ResponseEntity.noContent().build();
    }
}
