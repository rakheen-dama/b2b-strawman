package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for writing off an unbilled disbursement. A reason is required. */
public record WriteOffRequest(
    @NotBlank(message = "reason is required")
        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        String reason) {}
