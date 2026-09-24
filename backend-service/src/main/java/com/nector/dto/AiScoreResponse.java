package com.nector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Mirrors the JSON response from the Python AI scoring service:
 * { "ai_score": 85, "category": "HOT", "reason": "...", "raw_response": "..." }
 */
@Data
public class AiScoreResponse {

    @JsonProperty("ai_score")
    private Integer aiScore;

    private String category;

    private String reason;

    @JsonProperty("raw_response")
    private String rawResponse;

    /** Optional: some Gemini model wrappers include model_name in the response. */
    @JsonProperty("model_name")
    private String modelName;
}
