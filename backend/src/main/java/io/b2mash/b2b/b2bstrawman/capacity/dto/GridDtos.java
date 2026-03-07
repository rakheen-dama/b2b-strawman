package io.b2mash.b2b.b2bstrawman.capacity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GridDtos {

  private GridDtos() {}

  public record TeamCapacityGrid(List<MemberRow> members, List<WeekSummary> weekSummaries) {}

  public record MemberRow(
      UUID memberId,
      String memberName,
      String avatarUrl,
      List<WeekCell> weeks,
      BigDecimal totalAllocated,
      BigDecimal totalCapacity,
      BigDecimal avgUtilizationPct) {}

  public record WeekCell(
      LocalDate weekStart,
      List<AllocationSlot> allocations,
      BigDecimal totalAllocated,
      BigDecimal effectiveCapacity,
      BigDecimal remainingCapacity,
      BigDecimal utilizationPct,
      boolean overAllocated,
      int leaveDays) {}

  public record AllocationSlot(UUID projectId, String projectName, BigDecimal hours) {}

  public record WeekSummary(
      LocalDate weekStart,
      BigDecimal teamTotalAllocated,
      BigDecimal teamTotalCapacity,
      BigDecimal teamUtilizationPct) {}

  public record ProjectStaffingResponse(
      UUID projectId,
      String projectName,
      List<StaffingMemberRow> members,
      BigDecimal totalPlannedHours,
      BigDecimal budgetHours,
      BigDecimal budgetUsedPct) {}

  public record StaffingMemberRow(
      UUID memberId,
      String memberName,
      List<StaffingWeekCell> weeks,
      BigDecimal totalAllocatedHours) {}

  public record StaffingWeekCell(LocalDate weekStart, BigDecimal allocatedHours) {}
}
