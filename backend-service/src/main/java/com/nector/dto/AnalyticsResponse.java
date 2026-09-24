package com.nector.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Analytics summary response.
 */
@Data
@Builder
public class AnalyticsResponse {

    /** Total number of leads in the system. */
    private long                    totalLeads;

    /** Count per category: {"HOT": 12, "WARM": 34, "COLD": 56} */
    private Map<String, Long>       categoryBreakdown;

    /** Average score per source channel: {"referral": 82.4, "website": 51.2} */
    private Map<String, Double>     avgScoreBySource;

    /** Top 5 hottest leads ordered by score descending. */
    private List<LeadResponse>      top5HottestLeads;
}
