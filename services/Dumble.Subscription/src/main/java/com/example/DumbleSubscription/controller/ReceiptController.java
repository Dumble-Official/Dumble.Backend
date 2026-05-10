package com.example.DumbleSubscription.controller;

import com.example.DumbleSubscription.domain.Receipt;
import com.example.DumbleSubscription.dto.CurrentUser;
import com.example.DumbleSubscription.exception.ResourceNotFoundException;
import com.example.DumbleSubscription.repository.ReceiptRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ReceiptController {

    private final ReceiptRepository repository;

    public ReceiptController(ReceiptRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me/receipts")
    public List<Receipt> myReceipts(@AuthenticationPrincipal CurrentUser user) {
        return repository.findByUserIdOrderByIssuedAtDesc(user.getId());
    }

    @GetMapping("/me/receipts/{id}")
    public Receipt getReceipt(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        Receipt r = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
        if (!r.getUserId().equals(user.getId()) && !"ADMIN".equals(user.getUserType())) {
            throw new ResourceNotFoundException("Receipt not found");
        }
        return r;
    }

    // PDF rendering (Decision 11.6 — bilingual EN+AR) is a follow-up. The
    // receipt data is the contract; rendering is a UX detail that can swap.
}
