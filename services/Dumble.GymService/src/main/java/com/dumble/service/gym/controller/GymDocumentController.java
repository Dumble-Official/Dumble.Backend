package com.dumble.service.gym.controller;

import com.dumble.service.gym.domain.dto.GymDocumentResponse;
import com.dumble.service.gym.service.GymDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gyms/{gymId}/documents")
@Tag(name = "gym document")
public class GymDocumentController {
    private final GymDocumentService gymDocumentService;

    @PostMapping("/upload")
    public ResponseEntity<GymDocumentResponse> uploadGymDocument(@PathVariable UUID gymId, @RequestPart("file") MultipartFile file) {
        return new ResponseEntity<>(gymDocumentService.uploadDocument(file, gymId), HttpStatus.CREATED);
    }

    @PostMapping("/bulk-upload")
    public ResponseEntity<List<GymDocumentResponse>> uploadMultipleDocuments(@PathVariable UUID gymId, @RequestPart("file") List<MultipartFile> files) {
        return new ResponseEntity<>(gymDocumentService.uploadMultipleDocuments(files, gymId), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<GymDocumentResponse>> getGymDocument(@PathVariable UUID gymId) {
        return new ResponseEntity<>(gymDocumentService.getGymDocuments(gymId), HttpStatus.OK);
    }

    @DeleteMapping("/delete/{documentId}")
    public ResponseEntity<Void> deleteGymDocument(@PathVariable Long gymId, @PathVariable Long documentId) {
        gymDocumentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
