package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto;

import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementApprovalStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementBillingStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Canonical response DTO for a {@code LegalDisbursement}. */
public record DisbursementResponse(
    UUID id,
    UUID projectId,
    UUID customerId,
    DisbursementCategory category,
    String description,
    BigDecimal amount,
    VatTreatment vatTreatment,
    BigDecimal vatAmount,
    DisbursementPaymentSource paymentSource,
    UUID trustTransactionId,
    LocalDate incurredDate,
    String supplierName,
    String supplierReference,
    UUID receiptDocumentId,
    DisbursementApprovalStatus approvalStatus,
    UUID approvedBy,
    Instant approvedAt,
    String approvalNotes,
    DisbursementBillingStatus billingStatus,
    UUID invoiceLineId,
    String writeOffReason,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public static DisbursementResponse from(LegalDisbursement d) {
    return new DisbursementResponse(
        d.getId(),
        d.getProjectId(),
        d.getCustomerId(),
        DisbursementCategory.fromString(d.getCategory()),
        d.getDescription(),
        d.getAmount(),
        VatTreatment.valueOf(d.getVatTreatment()),
        d.getVatAmount(),
        DisbursementPaymentSource.valueOf(d.getPaymentSource()),
        d.getTrustTransactionId(),
        d.getIncurredDate(),
        d.getSupplierName(),
        d.getSupplierReference(),
        d.getReceiptDocumentId(),
        DisbursementApprovalStatus.valueOf(d.getApprovalStatus()),
        d.getApprovedBy(),
        d.getApprovedAt(),
        d.getApprovalNotes(),
        DisbursementBillingStatus.valueOf(d.getBillingStatus()),
        d.getInvoiceLineId(),
        d.getWriteOffReason(),
        d.getCreatedBy(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }
}
