package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for both approve and reject actions on a pending disbursement. Notes are optional
 * but size-capped.
 */
public record ApprovalDecisionRequest(
    @Size(max = 1000, message = "notes must not exceed 1000 characters") String notes) {}
