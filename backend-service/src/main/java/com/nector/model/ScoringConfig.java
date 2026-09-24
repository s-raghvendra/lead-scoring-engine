package com.nector.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * MySQL entity for rule-based scoring configuration.
 * Each row represents one rule/factor with its bonus weight.
 */
@Entity
@Table(name = "scoring_config")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ScoringConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique rule identifier, e.g. {@code source_referral}, {@code budget_gte_100000}. */
    @Column(name = "factor_name", nullable = false, unique = true, length = 100)
    private String factorName;

    /** Bonus points added to the raw AI score (can be negative to penalise). */
    @Column(nullable = false)
    private Integer weight;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
