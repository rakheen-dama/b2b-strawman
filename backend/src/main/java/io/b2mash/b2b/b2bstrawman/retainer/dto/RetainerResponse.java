package io.b2mash.b2b.b2bstrawman.retainer.dto;

import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerStatus;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RetainerResponse(
    UUID id,
    UUID customerId,
    UUID scheduleId,
    String customerName,
    String name,
    RetainerType type,
    RetainerStatus status,
    RetainerFrequency frequency,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal allocatedHours,
    BigDecimal periodFee,
    RolloverPolicy rolloverPolicy,
    BigDecimal rolloverCapHours,
    String notes,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    PeriodSummary currentPeriod,
    List<PeriodSummary> recentPeriods) {

  public static RetainerResponse from(
      RetainerAgreement a,
      String customerName,
      PeriodSummary currentPeriod,
      List<PeriodSummary> recentPeriods) {
    return new RetainerResponse(
        a.getId(),
        a.getCustomerId(),
        a.getScheduleId(),
        customerName,
        a.getName(),
        a.getType(),
        a.getStatus(),
        a.getFrequency(),
        a.getStartDate(),
        a.getEndDate(),
        a.getAllocatedHours(),
        a.getPeriodFee(),
        a.getRolloverPolicy(),
        a.getRolloverCapHours(),
        a.getNotes(),
        a.getCreatedBy(),
        a.getCreatedAt(),
        a.getUpdatedAt(),
        currentPeriod,
        recentPeriods);
  }
}
