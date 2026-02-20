package io.b2mash.b2b.b2bstrawman.retainer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodReadyToCloseView(
    UUID periodId,
    UUID agreementId,
    String agreementName,
    UUID customerId,
    String customerName,
    LocalDate periodEnd,
    BigDecimal consumedHours,
    BigDecimal allocatedHours) {}
