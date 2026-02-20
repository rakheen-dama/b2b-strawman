package io.b2mash.b2b.b2bstrawman.retainer.dto;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response DTO for POST /api/retainers/{id}/periods/current/close. Distinct from
 * RetainerPeriodService.PeriodCloseResult (the service-layer record).
 */
public record PeriodCloseResult(
    PeriodSummary closedPeriod,
    GeneratedInvoiceSummary generatedInvoice,
    PeriodSummary nextPeriod) {

  public record GeneratedInvoiceSummary(
      UUID id,
      String invoiceNumber,
      InvoiceStatus status,
      String currency,
      BigDecimal total,
      List<InvoiceLineSummary> lines) {}

  public record InvoiceLineSummary(
      String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

  public static PeriodCloseResult from(
      RetainerPeriodService.PeriodCloseResult result, List<InvoiceLine> lines) {
    Invoice inv = result.generatedInvoice();
    List<InvoiceLineSummary> lineSummaries =
        lines.stream()
            .map(
                l ->
                    new InvoiceLineSummary(
                        l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getAmount()))
            .toList();
    GeneratedInvoiceSummary invoiceSummary =
        new GeneratedInvoiceSummary(
            inv.getId(),
            inv.getInvoiceNumber(),
            inv.getStatus(),
            inv.getCurrency(),
            inv.getTotal(),
            lineSummaries);

    PeriodSummary closedDto = PeriodSummary.from(result.closedPeriod());
    PeriodSummary nextDto =
        result.nextPeriod() != null ? PeriodSummary.from(result.nextPeriod()) : null;

    return new PeriodCloseResult(closedDto, invoiceSummary, nextDto);
  }
}
