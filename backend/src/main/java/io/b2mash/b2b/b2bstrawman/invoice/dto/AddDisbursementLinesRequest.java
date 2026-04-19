package io.b2mash.b2b.b2bstrawman.invoice.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Request body for appending DISBURSEMENT-source lines to an existing DRAFT invoice.
 *
 * <p>See {@code POST /api/invoices/{id}/disbursement-lines}. Each id must reference an APPROVED +
 * UNBILLED legal disbursement belonging to the invoice's customer. On success each disbursement
 * transitions to BILLED and is linked to the new invoice line.
 */
public record AddDisbursementLinesRequest(@NotEmpty List<UUID> disbursementIds) {}
