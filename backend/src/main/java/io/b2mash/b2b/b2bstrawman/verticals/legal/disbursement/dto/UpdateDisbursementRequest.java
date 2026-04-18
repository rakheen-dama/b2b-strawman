package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH body for updating an existing {@code LegalDisbursement}.
 *
 * <p>All fields are nullable so that omitted fields are "leave unchanged". The service rejects
 * updates against disbursements that are not in {@code DRAFT} or {@code PENDING_APPROVAL}.
 */
public record UpdateDisbursementRequest(
    DisbursementCategory category,
    @Size(max = 5000, message = "description must not exceed 5000 characters") String description,
    @Positive(message = "amount must be positive") BigDecimal amount,
    VatTreatment vatTreatment,
    LocalDate incurredDate,
    @Size(max = 200, message = "supplierName must not exceed 200 characters") String supplierName,
    @Size(max = 100, message = "supplierReference must not exceed 100 characters")
        String supplierReference,
    UUID receiptDocumentId) {}
