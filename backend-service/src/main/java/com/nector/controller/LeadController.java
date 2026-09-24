package com.nector.controller;

import com.nector.dto.*;
import com.nector.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing all lead scoring endpoints.
 *
 * <pre>
 * POST  /api/v1/leads              — Score a single lead
 * POST  /api/v1/leads/batch        — Score up to 50 leads
 * GET   /api/v1/leads              — List all leads
 * GET   /api/v1/leads/{id}         — Get single lead by ID
 * GET   /api/v1/leads/analytics    — Aggregate analytics
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    // ── Single lead ──────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<LeadResponse> scoreLead(
            @Valid @RequestBody LeadRequest request) {
        LeadResponse response = leadService.createAndScore(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Batch ────────────────────────────────────────────────────────────

    @PostMapping("/batch")
    public ResponseEntity<BatchLeadResponse> scoreBatch(
            @Valid @RequestBody BatchLeadRequest request) {
        BatchLeadResponse response = leadService.createAndScoreBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Query ────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<LeadResponse>> listLeads() {
        return ResponseEntity.ok(leadService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadById(@PathVariable Long id) {
        return ResponseEntity.ok(leadService.getById(id));
    }

    // ── Analytics ────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> analytics() {
        return ResponseEntity.ok(leadService.getAnalytics());
    }
}
