package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for the write-off endpoint. A non-blank reason is required. */
public record WriteOffRequest(
    @NotBlank(message = "reason is required")
        @Size(max = 5000, message = "reason must not exceed 5000 characters")
        String reason) {}
