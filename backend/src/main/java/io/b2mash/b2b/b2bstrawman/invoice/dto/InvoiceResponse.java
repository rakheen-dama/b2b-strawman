package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID customerId,
    String invoiceNumber,
    InvoiceStatus status,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal total,
    String notes,
    String paymentTerms,
    String paymentReference,
    Instant paidAt,
    String customerName,
    String customerEmail,
    String customerAddress,
    String orgName,
    UUID createdBy,
    String createdByName,
    UUID approvedBy,
    String approvedByName,
    Instant createdAt,
    Instant updatedAt,
    List<InvoiceLineResponse> lines,
    Map<String, Object> customFields,
    List<UUID> appliedFieldGroups) {

  public static InvoiceResponse from(
      Invoice invoice, List<InvoiceLineResponse> lines, Map<UUID, String> memberNames) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getCustomerId(),
        invoice.getInvoiceNumber(),
        invoice.getStatus(),
        invoice.getCurrency(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getSubtotal(),
        invoice.getTaxAmount(),
        invoice.getTotal(),
        invoice.getNotes(),
        invoice.getPaymentTerms(),
        invoice.getPaymentReference(),
        invoice.getPaidAt(),
        invoice.getCustomerName(),
        invoice.getCustomerEmail(),
        invoice.getCustomerAddress(),
        invoice.getOrgName(),
        invoice.getCreatedBy(),
        invoice.getCreatedBy() != null ? memberNames.get(invoice.getCreatedBy()) : null,
        invoice.getApprovedBy(),
        invoice.getApprovedBy() != null ? memberNames.get(invoice.getApprovedBy()) : null,
        invoice.getCreatedAt(),
        invoice.getUpdatedAt(),
        lines,
        invoice.getCustomFields() != null ? invoice.getCustomFields() : Map.of(),
        invoice.getAppliedFieldGroups());
  }
}
