package io.b2mash.b2b.b2bstrawman.capacity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class UtilizationDtos {

  private UtilizationDtos() {}

  public record WeekUtilization(
      LocalDate weekStart,
      BigDecimal effectiveCapacity,
      BigDecimal plannedHours,
      BigDecimal actualHours,
      BigDecimal billableActualHours,
      BigDecimal plannedUtilizationPct,
      BigDecimal actualUtilizationPct,
      BigDecimal billableUtilizationPct) {}

  public record MemberUtilizationSummary(
      UUID memberId,
      String memberName,
      BigDecimal weeklyCapacity,
      BigDecimal totalPlannedHours,
      BigDecimal totalActualHours,
      BigDecimal totalBillableHours,
      BigDecimal avgPlannedUtilizationPct,
      BigDecimal avgActualUtilizationPct,
      BigDecimal avgBillableUtilizationPct,
      int overAllocatedWeeks,
      List<WeekUtilization> weeks) {}

  public record TeamUtilizationResponse(
      List<MemberUtilizationSummary> members, TeamAverages teamAverages) {}

  public record TeamAverages(
      BigDecimal avgPlannedUtilizationPct,
      BigDecimal avgActualUtilizationPct,
      BigDecimal avgBillableUtilizationPct) {}
}
