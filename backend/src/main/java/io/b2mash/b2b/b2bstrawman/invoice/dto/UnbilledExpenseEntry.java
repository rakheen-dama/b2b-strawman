package io.b2mash.b2b.b2bstrawman.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UnbilledExpenseEntry(
    UUID id,
    UUID projectId,
    String projectName,
    LocalDate date,
    String description,
    BigDecimal amount,
    String currency,
    String category,
    BigDecimal markupPercent,
    BigDecimal billableAmount,
    String notes) {}
