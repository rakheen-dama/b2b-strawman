package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when an invoice transitions to SENT, PAID, or VOID to sync to the portal read-model.
 */
public final class InvoiceSyncEvent extends PortalDomainEvent {

  private final UUID invoiceId;
  private final UUID customerId;
  private final String invoiceNumber;
  private final String status;
  private final LocalDate issueDate;
  private final LocalDate dueDate;
  private final BigDecimal subtotal;
  private final BigDecimal taxAmount;
  private final BigDecimal total;
  private final String currency;
  private final String notes;
  private final String paymentUrl;
  private final String paymentSessionId;

  public InvoiceSyncEvent(
      UUID invoiceId,
      UUID customerId,
      String invoiceNumber,
      String status,
      LocalDate issueDate,
      LocalDate dueDate,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal total,
      String currency,
      String notes,
      String paymentUrl,
      String paymentSessionId,
      String orgId,
      String tenantId) {
    super(orgId, tenantId);
    this.invoiceId = invoiceId;
    this.customerId = customerId;
    this.invoiceNumber = invoiceNumber;
    this.status = status;
    this.issueDate = issueDate;
    this.dueDate = dueDate;
    this.subtotal = subtotal;
    this.taxAmount = taxAmount;
    this.total = total;
    this.currency = currency;
    this.notes = notes;
    this.paymentUrl = paymentUrl;
    this.paymentSessionId = paymentSessionId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotal() {
    return total;
  }

  public String getCurrency() {
    return currency;
  }

  public String getNotes() {
    return notes;
  }

  public String getPaymentUrl() {
    return paymentUrl;
  }

  public String getPaymentSessionId() {
    return paymentSessionId;
  }
}
