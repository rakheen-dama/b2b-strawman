package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for both approve and reject endpoints. The {@code notes} field is optional — callers may
 * send an empty body or omit the field.
 */
public record ApprovalDecisionRequest(
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes) {}
