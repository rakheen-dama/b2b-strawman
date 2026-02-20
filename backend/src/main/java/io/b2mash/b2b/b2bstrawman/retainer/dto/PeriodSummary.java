package io.b2mash.b2b.b2bstrawman.retainer.dto;

import io.b2mash.b2b.b2bstrawman.retainer.PeriodStatus;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodSummary(
    UUID id,
    LocalDate periodStart,
    LocalDate periodEnd,
    PeriodStatus status,
    BigDecimal allocatedHours,
    BigDecimal baseAllocatedHours,
    BigDecimal consumedHours,
    BigDecimal remainingHours,
    BigDecimal rolloverHoursIn,
    BigDecimal overageHours,
    BigDecimal rolloverHoursOut,
    UUID invoiceId,
    Instant closedAt,
    UUID closedBy,
    boolean readyToClose) {

  public static PeriodSummary from(RetainerPeriod p) {
    boolean readyToClose =
        p.getStatus() == PeriodStatus.OPEN && !p.getPeriodEnd().isAfter(LocalDate.now());
    return new PeriodSummary(
        p.getId(),
        p.getPeriodStart(),
        p.getPeriodEnd(),
        p.getStatus(),
        p.getAllocatedHours(),
        p.getBaseAllocatedHours(),
        p.getConsumedHours(),
        p.getRemainingHours(),
        p.getRolloverHoursIn(),
        p.getOverageHours(),
        p.getRolloverHoursOut(),
        p.getInvoiceId(),
        p.getClosedAt(),
        p.getClosedBy(),
        readyToClose);
  }
}
