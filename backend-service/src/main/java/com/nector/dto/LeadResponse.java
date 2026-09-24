package com.nector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for a single scored (or pending) lead.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadResponse {

    private Long            id;
    private String          name;
    private String          email;
    private String          phone;
    private String          source;
    private String          company;
    private BigDecimal      budget;
    private String          message;
    private String          category;
    private Integer         latestScore;
    private String          status;
    private Boolean         isRepeatLead;
    private LocalDateTime   createdAt;
    private LocalDateTime   updatedAt;
    private String          scoringNote;  // e.g. "AI service unavailable; scored pending"
}
