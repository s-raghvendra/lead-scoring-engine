package com.nector.model.postgres;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PostgreSQL entity for the immutable scoring audit trail.
 * Stores the raw AI JSON and the rule-breakdown as JSONB columns.
 */
@Entity
@Table(name = "score_audit")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScoreAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK reference to the lead in MySQL (no JPA relation — cross-DB). */
    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    /** Name of the Gemini model that produced the AI score. */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * Raw JSON object returned by the AI service
     * (ai_score, category, reason, raw_response).
     */
    @Type(JsonBinaryType.class)
    @Column(name = "raw_ai_response", columnDefinition = "jsonb")
    private Map<String, Object> rawAiResponse;

    /**
     * Breakdown of which rules fired and how many bonus points each added.
     * e.g. {"source_referral": 15, "budget_gte_100000": 10}
     */
    @Type(JsonBinaryType.class)
    @Column(name = "rule_breakdown", columnDefinition = "jsonb")
    private Map<String, Integer> ruleBreakdown;

    /** Final clamped composite score (AI score + rule bonuses). */
    @Column(name = "final_score")
    private Integer finalScore;

    @CreationTimestamp
    @Column(name = "scored_at", nullable = false, updatable = false)
    private LocalDateTime scoredAt;
}
