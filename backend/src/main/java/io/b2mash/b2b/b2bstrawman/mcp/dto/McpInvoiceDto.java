package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.PaymentEventResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Invoice projection for {@code list_invoices} (compact row) and {@code get_invoice} (detail).
 * Money is carried as minor units (long) — {@link InvoiceResponse} carries its own {@code
 * currency}, so no settings lookup is needed. For the detail view, {@code lines} and {@code
 * payments} are capped at the MCP page max and {@code truncated} is set when either collection was
 * clipped.
 *
 * @param id invoice id
 * @param customerId the client this invoice is for
 * @param customerName the client name
 * @param invoiceNumber the human-facing invoice number
 * @param status invoice status (DRAFT/APPROVED/SENT/PAID/VOID)
 * @param currency 3-letter currency code
 * @param issueDate issue date
 * @param dueDate due date
 * @param subtotalMinor subtotal in minor units
 * @param taxMinor tax in minor units
 * @param totalMinor total in minor units
 * @param lines invoice line items (detail only, capped; {@code null} in list rows)
 * @param payments payment events (detail only, capped; {@code null} in list rows)
 * @param truncated true when lines or payments were clipped to the cap
 */
public record McpInvoiceDto(
    UUID id,
    UUID customerId,
    String customerName,
    String invoiceNumber,
    String status,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    long subtotalMinor,
    long taxMinor,
    long totalMinor,
    List<Line> lines,
    List<Payment> payments,
    boolean truncated) {

  /** One invoice line item. */
  public record Line(
      UUID id,
      UUID projectId,
      String projectName,
      String lineType,
      String description,
      long amountMinor) {}

  /** One payment event against the invoice. */
  public record Payment(
      UUID id, String status, long amountMinor, String currency, String reference) {}

  private static long minor(BigDecimal major) {
    return major == null ? 0L : major.movePointRight(2).longValueExact();
  }

  /** Compact list-row projection (no lines/payments). */
  public static McpInvoiceDto listItem(InvoiceResponse inv) {
    return new McpInvoiceDto(
        inv.id(),
        inv.customerId(),
        inv.customerName(),
        inv.invoiceNumber(),
        inv.status() != null ? inv.status().name() : null,
        inv.currency(),
        inv.issueDate(),
        inv.dueDate(),
        minor(inv.subtotal()),
        minor(inv.taxAmount()),
        minor(inv.total()),
        null,
        null,
        false);
  }

  /**
   * Detail projection capping lines + payments at {@code maxItems}. {@code truncated} is true when
   * either source collection exceeded the cap.
   */
  public static McpInvoiceDto detail(
      InvoiceResponse inv, List<PaymentEventResponse> paymentEvents, int maxItems) {
    List<InvoiceLineResponse> srcLines = inv.lines() == null ? List.of() : inv.lines();
    List<PaymentEventResponse> srcPayments = paymentEvents == null ? List.of() : paymentEvents;
    boolean truncated = srcLines.size() > maxItems || srcPayments.size() > maxItems;

    List<Line> lines =
        srcLines.stream()
            .limit(maxItems)
            .map(
                l ->
                    new Line(
                        l.id(),
                        l.projectId(),
                        l.projectName(),
                        l.lineType() != null ? l.lineType().name() : null,
                        l.description(),
                        minor(l.amount())))
            .toList();
    List<Payment> payments =
        srcPayments.stream()
            .limit(maxItems)
            .map(
                p ->
                    new Payment(
                        p.id(),
                        p.status() != null ? p.status().name() : null,
                        minor(p.amount()),
                        p.currency(),
                        p.paymentReference()))
            .toList();

    return new McpInvoiceDto(
        inv.id(),
        inv.customerId(),
        inv.customerName(),
        inv.invoiceNumber(),
        inv.status() != null ? inv.status().name() : null,
        inv.currency(),
        inv.issueDate(),
        inv.dueDate(),
        minor(inv.subtotal()),
        minor(inv.taxAmount()),
        minor(inv.total()),
        lines,
        payments,
        truncated);
  }
}
