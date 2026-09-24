package com.nector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Batch request wrapping up to 50 lead payloads.
 */
@Data
public class BatchLeadRequest {

    @NotEmpty(message = "Leads list must not be empty")
    @Size(max = 50, message = "Batch size must not exceed 50 leads")
    @Valid
    private List<LeadRequest> leads;
}
