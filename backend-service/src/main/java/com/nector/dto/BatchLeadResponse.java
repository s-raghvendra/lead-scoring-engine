package com.nector.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for a batch scoring operation.
 */
@Data
@Builder
public class BatchLeadResponse {

    private int                 total;
    private int                 scored;
    private int                 pending;
    private int                 duplicates;
    private List<LeadResponse>  results;
}
