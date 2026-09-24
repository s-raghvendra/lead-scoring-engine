package com.nector.service;

import com.nector.dto.*;
import com.nector.model.mysql.Lead;
import com.nector.model.mysql.Lead.Category;
import com.nector.model.mysql.Lead.LeadStatus;
import com.nector.model.postgres.ScoreAudit;
import com.nector.repository.mysql.LeadRepository;
import com.nector.repository.postgres.ScoreAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for lead creation, duplicate detection, hybrid scoring,
 * audit persistence, batch processing, and analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository           leadRepository;
    private final ScoreAuditRepository     auditRepository;
    private final AiScoringClient          aiScoringClient;
    private final HybridScoringEngine      hybridScoringEngine;

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Creates or updates a lead (duplicate detection), then attempts to score it.
     * If the AI service is unavailable, the lead is saved with PENDING_SCORE status.
     */
    @Transactional("mysqlTransactionManager")
    public LeadResponse createAndScore(LeadRequest request) {
        // 1. Duplicate detection
        Optional<Lead> existing = leadRepository.findByEmailOrPhone(
                request.getEmail().toLowerCase(), request.getPhone());

        Lead lead;
        boolean isRepeat = false;

        if (existing.isPresent()) {
            lead     = updateExistingLead(existing.get(), request);
            isRepeat = true;
            log.info("Duplicate lead detected for email={} | phone={}", request.getEmail(), request.getPhone());
        } else {
            lead = buildNewLead(request);
        }

        lead = leadRepository.save(lead);

        // 2. Attempt AI + hybrid scoring
        return scoreAndPersist(lead, request, isRepeat);
    }

    /**
     * Scores a batch of up to 50 leads sequentially.
     */
    @Transactional("mysqlTransactionManager")
    public BatchLeadResponse createAndScoreBatch(BatchLeadRequest batchRequest) {
        List<LeadResponse> results = new ArrayList<>();

        for (LeadRequest req : batchRequest.getLeads()) {
            try {
                results.add(createAndScore(req));
            } catch (Exception ex) {
                log.error("Batch item failed for email={}: {}", req.getEmail(), ex.getMessage());
                // Return a partial failure entry so the batch always responds
                results.add(LeadResponse.builder()
                        .email(req.getEmail())
                        .name(req.getName())
                        .status(LeadStatus.PENDING_SCORE.name())
                        .scoringNote("Processing error: " + ex.getMessage())
                        .build());
            }
        }

        long scored    = results.stream().filter(r -> "SCORED".equals(r.getStatus())).count();
        long pending   = results.stream().filter(r -> "PENDING_SCORE".equals(r.getStatus())).count();
        long duplicate = results.stream().filter(r -> Boolean.TRUE.equals(r.getIsRepeatLead())).count();

        return BatchLeadResponse.builder()
                .total(results.size())
                .scored((int) scored)
                .pending((int) pending)
                .duplicates((int) duplicate)
                .results(results)
                .build();
    }

    /**
     * Returns aggregate analytics over all leads.
     */
    public AnalyticsResponse getAnalytics() {
        long total = leadRepository.count();

        // Category breakdown
        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : leadRepository.countByCategory()) {
            categoryBreakdown.put(
                    row[0] != null ? row[0].toString() : "UNKNOWN",
                    ((Number) row[1]).longValue());
        }

        // Average score per source
        Map<String, Double> avgScoreBySource = new LinkedHashMap<>();
        for (Object[] row : leadRepository.avgScoreBySource()) {
            if (row[1] != null) {
                avgScoreBySource.put(
                        row[0].toString(),
                        Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0);
            }
        }

        // Top 5 hottest leads
        List<Lead> topLeads = leadRepository.findTopHotLeads(PageRequest.of(0, 5));
        List<LeadResponse> top5 = topLeads.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return AnalyticsResponse.builder()
                .totalLeads(total)
                .categoryBreakdown(categoryBreakdown)
                .avgScoreBySource(avgScoreBySource)
                .top5HottestLeads(top5)
                .build();
    }

    /**
     * Retrieves a single lead by ID.
     */
    public LeadResponse getById(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lead not found with id: " + id));
        return toResponse(lead);
    }

    /**
     * Returns all leads (pageable in future; returns all for now).
     */
    public List<LeadResponse> getAll() {
        return leadRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    //  Private helpers
    // =========================================================================

    private LeadResponse scoreAndPersist(Lead lead, LeadRequest request, boolean isRepeat) {
        // Call AI service (returns empty on any failure)
        Optional<AiScoreResponse> aiOpt = aiScoringClient.score(request);

        if (aiOpt.isEmpty()) {
            log.warn("AI service unavailable for lead id={}; saving as PENDING_SCORE", lead.getId());
            lead.setStatus(LeadStatus.PENDING_SCORE);
            lead = leadRepository.save(lead);
            return toResponse(lead, "AI service unavailable; lead queued for deferred scoring");
        }

        AiScoreResponse aiResponse = aiOpt.get();

        // Apply hybrid rule-based bonuses
        HybridScoringEngine.ScoringResult scoringResult =
                hybridScoringEngine.applyRules(aiResponse, request);

        // Apply repeat-lead bonus if applicable
        int finalScore = scoringResult.finalScore();
        Map<String, Integer> breakdown = new HashMap<>(scoringResult.ruleBreakdown());

        if (isRepeat) {
            int repeatBonus = getRepeatLeadBonus();
            if (repeatBonus > 0) {
                finalScore = Math.min(100, finalScore + repeatBonus);
                breakdown.put("repeat_lead_bonus", repeatBonus);
                log.debug("Repeat lead bonus applied: +{}", repeatBonus);
            }
        }

        Category category = deriveCategory(finalScore);

        // Persist updated lead in MySQL
        lead.setLatestScore(finalScore);
        lead.setCategory(category);
        lead.setStatus(LeadStatus.SCORED);
        lead = leadRepository.save(lead);

        // Persist audit record in PostgreSQL (best-effort, don't fail the request)
        persistAudit(lead, aiResponse, breakdown, finalScore);

        return toResponse(lead);
    }

    private void persistAudit(Lead lead,
                               AiScoreResponse ai,
                               Map<String, Integer> breakdown,
                               int finalScore) {
        try {
            Map<String, Object> rawAiMap = new LinkedHashMap<>();
            rawAiMap.put("ai_score",     ai.getAiScore());
            rawAiMap.put("category",     ai.getCategory());
            rawAiMap.put("reason",       ai.getReason());
            rawAiMap.put("raw_response", ai.getRawResponse());

            ScoreAudit audit = ScoreAudit.builder()
                    .leadId(lead.getId())
                    .modelName(ai.getModelName() != null ? ai.getModelName() : "gemini-dynamic")
                    .rawAiResponse(rawAiMap)
                    .ruleBreakdown(breakdown)
                    .finalScore(finalScore)
                    .build();

            auditRepository.save(audit);
            log.debug("Audit record saved for lead id={}", lead.getId());
        } catch (Exception ex) {
            // Audit failure must NEVER roll back the main lead transaction
            log.error("Failed to persist audit for lead id={}: {}", lead.getId(), ex.getMessage());
        }
    }

    private Lead buildNewLead(LeadRequest req) {
        return Lead.builder()
                .name(req.getName())
                .email(req.getEmail().toLowerCase())
                .phone(req.getPhone())
                .message(req.getMessage())
                .source(req.getSource())
                .budget(req.getBudget())
                .company(req.getCompany())
                .status(LeadStatus.PENDING_SCORE)
                .isRepeatLead(false)
                .build();
    }

    private Lead updateExistingLead(Lead existing, LeadRequest req) {
        existing.setName(req.getName());
        existing.setMessage(req.getMessage());
        existing.setSource(req.getSource());
        existing.setBudget(req.getBudget());
        existing.setCompany(req.getCompany());
        existing.setIsRepeatLead(true);
        existing.setStatus(LeadStatus.PENDING_SCORE); // will be updated after scoring
        return existing;
    }

    private Category deriveCategory(int score) {
        if (score >= 70) return Category.HOT;
        if (score >= 40) return Category.WARM;
        return Category.COLD;
    }

    private int getRepeatLeadBonus() {
        // Could be loaded from ScoringConfigRepository but we use a safe default
        // since the rule is evaluated separately from the engine to avoid double-
        // counting (the engine's switch returns false for repeat_lead_bonus).
        try {
            return 8; // matches seed data default
        } catch (Exception ex) {
            return 0;
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private LeadResponse toResponse(Lead lead) {
        return toResponse(lead, null);
    }

    private LeadResponse toResponse(Lead lead, String note) {
        return LeadResponse.builder()
                .id(lead.getId())
                .name(lead.getName())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .source(lead.getSource())
                .company(lead.getCompany())
                .budget(lead.getBudget())
                .message(lead.getMessage())
                .category(lead.getCategory() != null ? lead.getCategory().name() : null)
                .latestScore(lead.getLatestScore())
                .status(lead.getStatus().name())
                .isRepeatLead(lead.getIsRepeatLead())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .scoringNote(note)
                .build();
    }
}
