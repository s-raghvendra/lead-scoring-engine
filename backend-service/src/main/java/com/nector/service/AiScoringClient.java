package com.nector.service;

import com.nector.dto.AiScoreResponse;
import com.nector.dto.LeadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Calls the Python AI scoring service via reactive WebClient.
 * Returns an empty Optional on any error (timeout, 5xx, unreachable)
 * so the caller can decide how to handle the fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiScoringClient {

    private final WebClient aiServiceWebClient;

    /**
     * Sends a lead to the Python AI service for scoring.
     *
     * @param request the lead payload
     * @return Optional containing the AI response, or empty if the service is unavailable
     */
    public Optional<AiScoreResponse> score(LeadRequest request) {
        try {
            AiScoreResponse response = aiServiceWebClient
                    .post()
                    .uri("/api/v1/score-lead")
                    .bodyValue(buildAiPayload(request))
                    .retrieve()
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(
                                                new RuntimeException("AI service 5xx: " + body))))
                    .bodyToMono(AiScoreResponse.class)
                    .block(Duration.ofSeconds(20)); // hard outer timeout

            if (response == null) {
                log.warn("AI service returned null body");
                return Optional.empty();
            }
            return Optional.of(response);

        } catch (WebClientRequestException ex) {
            log.warn("AI service unreachable: {}", ex.getMessage());
            return Optional.empty();
        } catch (WebClientResponseException ex) {
            log.warn("AI service HTTP error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("AI service call failed ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Builds the payload matching the Python AI service's {@code Lead} Pydantic schema.
     */
    private java.util.Map<String, Object> buildAiPayload(LeadRequest req) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("name",    req.getName());
        map.put("email",   req.getEmail().toLowerCase());
        map.put("phone",   req.getPhone());
        map.put("message", req.getMessage());
        map.put("source",  req.getSource());
        if (req.getBudget()  != null) map.put("budget",  req.getBudget());
        if (req.getCompany() != null) map.put("company", req.getCompany());
        return map;
    }
}
