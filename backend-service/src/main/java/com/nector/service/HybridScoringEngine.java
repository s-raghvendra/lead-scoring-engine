package com.nector.service;

import com.nector.dto.AiScoreResponse;
import com.nector.dto.LeadRequest;
import com.nector.model.mysql.ScoringConfig;
import com.nector.repository.mysql.ScoringConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid scoring engine that combines the AI score with rule-based bonus points
 * loaded from the {@code scoring_config} table.
 *
 * <p>Rules follow the pattern: if a named condition is satisfied for the lead,
 * the corresponding weight is added to the raw AI score. The result is clamped
 * to [0, 100].
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridScoringEngine {

    private final ScoringConfigRepository configRepository;

    /**
     * Applies rule-based bonuses to the AI score.
     *
     * @param aiResponse the response from the AI service
     * @param request    the lead being scored (used to evaluate rules)
     * @return a {@link ScoringResult} with the final score and breakdown
     */
    public ScoringResult applyRules(AiScoreResponse aiResponse, LeadRequest request) {
        int baseScore      = aiResponse.getAiScore() != null ? aiResponse.getAiScore() : 0;
        int composite      = baseScore;
        Map<String, Integer> breakdown = new HashMap<>();

        List<ScoringConfig> activeRules = configRepository.findByIsActiveTrue();

        for (ScoringConfig rule : activeRules) {
            if (isRuleSatisfied(rule.getFactorName(), request)) {
                composite += rule.getWeight();
                breakdown.put(rule.getFactorName(), rule.getWeight());
                log.debug("Rule '{}' fired: +{} pts", rule.getFactorName(), rule.getWeight());
            }
        }

        int finalScore = Math.max(0, Math.min(100, composite));
        log.debug("Scoring complete: base={}, final={}, breakdown={}", baseScore, finalScore, breakdown);

        return new ScoringResult(finalScore, breakdown);
    }

    /**
     * Evaluates a single named rule against the lead data.
     * Naming convention: {@code <dimension>_<condition>}
     */
    private boolean isRuleSatisfied(String factorName, LeadRequest req) {
        return switch (factorName) {
            // ── Source rules ─────────────────────────────────────────────
            case "source_referral" ->
                    equalsIgnoreCase(req.getSource(), "referral");
            case "source_partner"  ->
                    equalsIgnoreCase(req.getSource(), "partner");
            case "source_website"  ->
                    equalsIgnoreCase(req.getSource(), "website");
            case "source_social"   ->
                    equalsIgnoreCase(req.getSource(), "social") ||
                    equalsIgnoreCase(req.getSource(), "social_media");

            // ── Budget rules ─────────────────────────────────────────────
            case "budget_gte_100000" ->
                    hasBudgetGte(req, 100_000);
            case "budget_gte_50000"  ->
                    hasBudgetGte(req, 50_000) && !hasBudgetGte(req, 100_000);
            case "budget_gte_10000"  ->
                    hasBudgetGte(req, 10_000) && !hasBudgetGte(req, 50_000);

            // ── Signal rules ─────────────────────────────────────────────
            case "company_present" ->
                    req.getCompany() != null && !req.getCompany().isBlank();
            case "message_length_gte_200" ->
                    req.getMessage() != null && req.getMessage().length() >= 200;

            // ── Repeat-lead bonus is applied externally (after DB check) ──
            case "repeat_lead_bonus" -> false;

            default -> {
                log.trace("Unknown rule factor '{}', skipping", factorName);
                yield false;
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private boolean hasBudgetGte(LeadRequest req, double threshold) {
        return req.getBudget() != null &&
               req.getBudget().compareTo(BigDecimal.valueOf(threshold)) >= 0;
    }

    // ── Result record ─────────────────────────────────────────────────────

    /**
     * Immutable result from the hybrid scoring engine.
     *
     * @param finalScore clamped composite score (0-100)
     * @param ruleBreakdown map of rule name -> points added
     */
    public record ScoringResult(int finalScore, Map<String, Integer> ruleBreakdown) {}
}
