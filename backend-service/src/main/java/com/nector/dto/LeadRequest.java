package com.nector.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating / scoring a single lead.
 */
@Data
public class LeadRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone is required")
    @Size(min = 5, max = 30)
    private String phone;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Source is required")
    @Size(max = 100)
    private String source;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal budget;

    @Size(max = 200)
    private String company;
}
