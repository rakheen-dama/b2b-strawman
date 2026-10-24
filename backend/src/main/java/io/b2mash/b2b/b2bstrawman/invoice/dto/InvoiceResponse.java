package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentEvent;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.invoice.TaxType;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID clientId,
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
    String clientName,
    String clientEmail,
    String clientAddress,
    String orgName,
    UUID createdBy,
    String createdByName,
    UUID approvedBy,
    String approvedByName,
    Instant createdAt,
    Instant updatedAt,
    List<InvoiceLineResponse> lines,
    Map<String, Object> customFields,
    List<UUID> appliedFieldGroups,
    String paymentSessionId,
    String paymentUrl,
    String paymentDestination,
    List<TaxBreakdownEntry> taxBreakdown,
    boolean taxInclusive,
    boolean hasPerLineTax,
    String poNumber,
    TaxType taxType,
    LocalDate billingPeriodStart,
    LocalDate billingPeriodEnd,
    /** Phase 88 U15 (ADR-347 A3): stored running total of posted payments, capped at total. */
    BigDecimal amountPaid,
    /** {@code total − amountPaid}, derived. */
    BigDecimal balanceDue,
    /** COMPLETED payment events, newest first. */
    List<PaymentLine> payments,
    /**
     * Non-blocking warnings emitted by the draft-creation path (soft prerequisites that would hard-
     * block invoice-send). Populated by {@code InvoiceCreationService.createDraft} and empty on
     * subsequent reads — the frontend uses this to show an inline banner on the just-created draft
     * without a separate API call. See GAP-L-62.
     */
    List<String> warnings) {

  /** One posted payment (§13.4.3 {@code payments[]}). */
  public record PaymentLine(
      UUID id,
      BigDecimal amount,
      PaymentMethod paymentMethod,
      String paymentReference,
      Instant recordedAt,
      String source) {

    public static PaymentLine from(PaymentEvent e) {
      return new PaymentLine(
          e.getId(),
          e.getAmount(),
          e.getPaymentMethod(),
          e.getPaymentReference(),
          e.getCreatedAt(),
          sourceOf(e.getProviderSlug()));
    }

    /**
     * provider_slug → wire source: manual → MANUAL, trust_fee_transfer → TRUST_TRANSFER, xero →
     * XERO, anything else (stripe/payfast/mock gateways) → GATEWAY.
     */
    static String sourceOf(String providerSlug) {
      if (providerSlug == null) {
        return "GATEWAY";
      }
      return switch (providerSlug) {
        case "manual" -> "MANUAL";
        case "trust_fee_transfer" -> "TRUST_TRANSFER";
        case "xero" -> "XERO";
        default -> "GATEWAY";
      };
    }
  }

  public static InvoiceResponse from(
      Invoice invoice,
      List<InvoiceLineResponse> lines,
      Map<UUID, String> memberNames,
      List<TaxBreakdownEntry> taxBreakdown,
      boolean taxInclusive,
      boolean hasPerLineTax) {
    return from(
        invoice,
        lines,
        memberNames,
        taxBreakdown,
        taxInclusive,
        hasPerLineTax,
        List.of(),
        List.of());
  }

  public static InvoiceResponse from(
      Invoice invoice,
      List<InvoiceLineResponse> lines,
      Map<UUID, String> memberNames,
      List<TaxBreakdownEntry> taxBreakdown,
      boolean taxInclusive,
      boolean hasPerLineTax,
      List<PaymentEvent> payments,
      List<String> warnings) {
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
        invoice.getAppliedFieldGroups() != null ? invoice.getAppliedFieldGroups() : List.of(),
        invoice.getPaymentSessionId(),
        invoice.getPaymentUrl(),
        invoice.getPaymentDestination(),
        taxBreakdown,
        taxInclusive,
        hasPerLineTax,
        invoice.getPoNumber(),
        invoice.getTaxType(),
        invoice.getBillingPeriodStart(),
        invoice.getBillingPeriodEnd(),
        invoice.getAmountPaid(),
        invoice.getBalanceDue(),
        payments != null ? payments.stream().map(PaymentLine::from).toList() : List.of(),
        warnings != null ? warnings : List.of());
  }
}
