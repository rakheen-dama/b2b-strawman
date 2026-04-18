package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new {@code LegalDisbursement}.
 *
 * <p>The resulting disbursement is created in {@code DRAFT} approval status and {@code UNBILLED}
 * billing status. {@code vatTreatment} is optional — the service applies a category-conventional
 * default when omitted. When {@code paymentSource = TRUST_ACCOUNT}, {@code trustTransactionId} is
 * required and service-validated against the linked {@code TrustTransaction}.
 */
public record CreateDisbursementRequest(
    @NotNull(message = "projectId is required") UUID projectId,
    @NotNull(message = "customerId is required") UUID customerId,
    @NotNull(message = "category is required") DisbursementCategory category,
    @NotBlank(message = "description is required")
        @Size(max = 5000, message = "description must not exceed 5000 characters")
        String description,
    @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
        BigDecimal amount,
    VatTreatment vatTreatment,
    @NotNull(message = "paymentSource is required") DisbursementPaymentSource paymentSource,
    UUID trustTransactionId,
    @NotNull(message = "incurredDate is required") LocalDate incurredDate,
    @NotBlank(message = "supplierName is required")
        @Size(max = 200, message = "supplierName must not exceed 200 characters")
        String supplierName,
    @Size(max = 100, message = "supplierReference must not exceed 100 characters")
        String supplierReference,
    UUID receiptDocumentId) {}
