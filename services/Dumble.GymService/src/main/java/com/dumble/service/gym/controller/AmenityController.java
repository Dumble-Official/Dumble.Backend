package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.AmenityDto;
import com.dumble.service.gym.service.AmenityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/amenities")
@Tag(name = "amenity")
public class AmenityController {
    private final AmenityService amenityService;

    @PostMapping("/add")
    public ResponseEntity<AmenityDto> createAmenity(@RequestBody @Valid AmenityDto request,
                                                    @RequestHeader("Authorization") String token) {
        return new  ResponseEntity<>(amenityService.createAmenity(request, token), HttpStatus.CREATED);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<AmenityDto> updateAmenity(@PathVariable Long id, @RequestBody @Valid AmenityDto request
    ,@RequestHeader("Authorization") String token) {
        return new ResponseEntity<>(amenityService.updateAmenity(id, request, token), HttpStatus.OK);
    }

    @DeleteMapping("delete/{id}")
    public ResponseEntity<Void> deleteAmenity(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        amenityService.deleteAmenity(id, token);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping
    public ResponseEntity<Page<AmenityDto>> getAllAmenities(Pageable pageable) {
        return new ResponseEntity<>(amenityService.getAllAmenities(pageable), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AmenityDto> getAmenityById(@PathVariable Long id) {
        return ResponseEntity.ok(amenityService.getAmenityById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AmenityDto>> searchAmenities(
            @RequestParam String keyword, Pageable pageable) {
        return ResponseEntity.ok(amenityService.searchAmenities(keyword, pageable));
    }

    @PatchMapping("toggle-amenity/{id}")
    public ResponseEntity<AmenityDto> toggleAmenityStatus(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(amenityService.toggleAmenityStatus(id, token));
    }

}
