package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for marking an approved disbursement as billed on a specific invoice line. */
public record MarkBilledRequest(
    @NotNull(message = "invoiceLineId is required") UUID invoiceLineId) {}
