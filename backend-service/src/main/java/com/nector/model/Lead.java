package com.nector.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MySQL entity representing an inbound lead.
 */
@Entity
@Table(
    name = "leads",
    indexes = {
        @Index(name = "idx_leads_email",    columnList = "email"),
        @Index(name = "idx_leads_phone",    columnList = "phone"),
        @Index(name = "idx_leads_status",   columnList = "status"),
        @Index(name = "idx_leads_category", columnList = "category"),
        @Index(name = "idx_leads_score",    columnList = "latest_score")
    }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(precision = 15, scale = 2)
    private BigDecimal budget;

    @Column(length = 200)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Category category;

    @Column(name = "latest_score")
    private Integer latestScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LeadStatus status = LeadStatus.PENDING_SCORE;

    @Column(name = "is_repeat_lead", nullable = false)
    @Builder.Default
    private Boolean isRepeatLead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Enums ──────────────────────────────────────────────────────────────

    public enum Category { HOT, WARM, COLD }

    public enum LeadStatus { SCORED, PENDING_SCORE, DUPLICATE }
}
