package io.b2mash.b2b.b2bstrawman.invoice.dto;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id,
    UUID projectId,
    String projectName,
    UUID timeEntryId,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal amount,
    int sortOrder) {

  public static InvoiceLineResponse from(InvoiceLine line, String projectName) {
    return new InvoiceLineResponse(
        line.getId(),
        line.getProjectId(),
        projectName,
        line.getTimeEntryId(),
        line.getDescription(),
        line.getQuantity(),
        line.getUnitPrice(),
        line.getAmount(),
        line.getSortOrder());
  }
}
