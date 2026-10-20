package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id,
    UUID matterId,
    String matterName,
    UUID timeEntryId,
    UUID expenseId,
    InvoiceLineType lineType,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal amount,
    int sortOrder,
    UUID taxRateId,
    String taxRateName,
    BigDecimal taxRatePercent,
    BigDecimal taxAmount,
    boolean taxExempt,
    UUID tariffItemId,
    String lineSource,
    String tariffItemNumber) {

  public static InvoiceLineResponse from(InvoiceLine line, String matterName) {
    return from(line, matterName, null);
  }

  public static InvoiceLineResponse from(
      InvoiceLine line, String matterName, String tariffItemNumber) {
    return new InvoiceLineResponse(
        line.getId(),
        line.getProjectId(),
        matterName,
        line.getTimeEntryId(),
        line.getExpenseId(),
        line.getLineType(),
        line.getDescription(),
        line.getQuantity(),
        line.getUnitPrice(),
        line.getAmount(),
        line.getSortOrder(),
        line.getTaxRateId(),
        line.getTaxRateName(),
        line.getTaxRatePercent(),
        line.getTaxAmount(),
        line.isTaxExempt(),
        line.getTariffItemId(),
        line.getLineSource(),
        tariffItemNumber);
  }
}
