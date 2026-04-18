package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for both approve and reject endpoints. Send {@code {}} with an optional {@code notes} field;
 * the {@code notes} field may be omitted but the JSON body itself is required.
 */
public record ApprovalDecisionRequest(
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes) {}
