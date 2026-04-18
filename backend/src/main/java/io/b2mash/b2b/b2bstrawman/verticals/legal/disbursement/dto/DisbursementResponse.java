package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full response DTO for a legal disbursement. Exposes every entity field. */
public record DisbursementResponse(
    UUID id,
    UUID projectId,
    UUID customerId,
    LocalDate incurredDate,
    DisbursementCategory category,
    String description,
    BigDecimal amount,
    String currency,
    VatTreatment vatTreatment,
    BigDecimal vatAmount,
    String supplierName,
    UUID receiptDocumentId,
    DisbursementPaymentSource paymentSource,
    UUID trustTransactionId,
    String approvalStatus,
    String approvalNotes,
    UUID approvedBy,
    Instant approvedAt,
    String billingStatus,
    UUID billedInvoiceLineId,
    String writeOffReason,
    Instant writtenOffAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {

  /** Maps a LegalDisbursement entity to the response DTO. */
  public static DisbursementResponse from(LegalDisbursement d) {
    return new DisbursementResponse(
        d.getId(),
        d.getProjectId(),
        d.getCustomerId(),
        d.getIncurredDate(),
        DisbursementCategory.valueOf(d.getCategory()),
        d.getDescription(),
        d.getAmount(),
        d.getCurrency(),
        VatTreatment.valueOf(d.getVatTreatment()),
        d.getVatAmount(),
        d.getSupplierName(),
        d.getReceiptDocumentId(),
        DisbursementPaymentSource.valueOf(d.getPaymentSource()),
        d.getTrustTransactionId(),
        d.getApprovalStatus(),
        d.getApprovalNotes(),
        d.getApprovedBy(),
        d.getApprovedAt(),
        d.getBillingStatus(),
        d.getBilledInvoiceLineId(),
        d.getWriteOffReason(),
        d.getWrittenOffAt(),
        d.getCreatedBy(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
