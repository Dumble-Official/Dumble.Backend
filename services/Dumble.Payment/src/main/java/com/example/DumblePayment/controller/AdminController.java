package com.example.DumblePayment.controller;

import com.example.DumblePayment.dto.ReconReport;
import com.example.DumblePayment.repository.ReconciliationRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Decision 7.3 — admin-facing reconciliation observability. Returns the
 * most recent run summaries; the dashboard plumbs them into the operator
 * view.
 */
@RestController
@RequestMapping("/admin/payment")
public class AdminController {

    private final ReconciliationRunRepository runRepository;

    public AdminController(ReconciliationRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @GetMapping("/recon")
    public ReconReport recon() {
        List<ReconReport.RunSummary> recent = runRepository
                .findAllByOrderByStartedAtDesc(PageRequest.of(0, 30))
                .stream()
                .map(ReconReport.RunSummary::from)
                .toList();
        return ReconReport.builder().recentRuns(recent).build();
    }
}
