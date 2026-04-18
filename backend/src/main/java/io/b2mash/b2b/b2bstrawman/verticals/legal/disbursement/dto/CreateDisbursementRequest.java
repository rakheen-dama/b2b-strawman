package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new legal disbursement. {@code vatTreatment} is optional — if null,
 * the service defaults by category (statutory pass-through categories → zero-rated). {@code
 * trustTransactionId} is required only when {@code paymentSource == TRUST_ACCOUNT}.
 */
public record CreateDisbursementRequest(
    @NotNull(message = "projectId is required") UUID projectId,
    @NotNull(message = "customerId is required") UUID customerId,
    @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description,
    @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
        BigDecimal amount,
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency,
    @NotNull(message = "category is required") DisbursementCategory category,
    VatTreatment vatTreatment,
    @NotNull(message = "paymentSource is required") DisbursementPaymentSource paymentSource,
    UUID trustTransactionId,
    @NotNull(message = "incurredDate is required") LocalDate incurredDate,
    @Size(max = 200, message = "supplierName must not exceed 200 characters")
        String supplierName) {}
