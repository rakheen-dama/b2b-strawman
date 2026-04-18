package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for updating a DRAFT legal disbursement. Fields are all optional on the wire, but
 * the service applies the update only if the disbursement is still in DRAFT. If {@code
 * paymentSource} changes to TRUST_ACCOUNT, {@code trustTransactionId} must be supplied.
 */
public record UpdateDisbursementRequest(
    LocalDate incurredDate,
    DisbursementCategory category,
    @Size(max = 2000, message = "description must not exceed 2000 characters") String description,
    @Positive(message = "amount must be positive") BigDecimal amount,
    String currency,
    VatTreatment vatTreatment,
    @Size(max = 200, message = "supplierName must not exceed 200 characters") String supplierName,
    DisbursementPaymentSource paymentSource,
    UUID trustTransactionId) {}
