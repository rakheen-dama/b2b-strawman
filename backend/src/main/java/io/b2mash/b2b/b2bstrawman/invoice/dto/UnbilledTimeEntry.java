package io.b2mash.b2b.b2bstrawman.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UnbilledTimeEntry(
    UUID id,
    String taskTitle,
    String memberName,
    LocalDate date,
    int durationMinutes,
    BigDecimal billingRateSnapshot,
    String billingRateCurrency,
    BigDecimal billableValue,
    String description) {}
