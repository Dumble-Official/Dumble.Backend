package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.GymCreateRequest;
import com.dumble.service.gym.domain.dto.GymResponse;
import com.dumble.service.gym.domain.dto.GymUpdateRequest;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import com.dumble.service.gym.service.GymService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/gyms")
@Tag(name = "gym")
public class GymController {
    private final GymService gymService;

    @PostMapping("/create")
    public ResponseEntity<GymResponse> createGym(@RequestBody GymCreateRequest request, @RequestHeader("Authorization") String token) {
        return new ResponseEntity<>(gymService.createGym(request, token), HttpStatus.CREATED);
    }

    @PutMapping("/update/{gymId}")
    public ResponseEntity<GymResponse> updateGym(@PathVariable UUID gymId, @RequestBody GymUpdateRequest request, @RequestHeader("Authorization") String token) {
        return new ResponseEntity<>(gymService.updateGym(gymId, request, token), HttpStatus.OK);
    }

    @DeleteMapping("/delete/{gymId}")
    public ResponseEntity<Void> deleteGym(@PathVariable UUID gymId, @RequestHeader("Authorization") String token) {
        gymService.deleteGym(gymId, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{gymId}")
    public ResponseEntity<GymResponse> getGym(@PathVariable UUID gymId) {
        return new ResponseEntity<>(gymService.getGymById(gymId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<Page<GymResponse>> gerAllGyms(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) GenderType genderType,
            Pageable pageable) {
        // Public discovery only ever shows admin-verified, ACTIVE gyms. The verified/status
        // filters are not client-controllable here, so a PENDING or unverified gym can never
        // leak into the public listing; moderation of other states lives behind /admin/gyms.
        return new ResponseEntity<>(
                gymService.getAllGyms(name, genderType, Boolean.TRUE, GymStatus.ACTIVE, pageable),
                HttpStatus.OK);
    }

    @GetMapping("/nearby")
    public ResponseEntity<Page<GymResponse>> getNearbyGyms(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10.0") Double distance,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(gymService.findNearbyGyms(lat, lng, distance, pageable));
    }
}
