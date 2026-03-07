package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.MemberUtilizationSummary;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.TeamAverages;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.TeamUtilizationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.UtilizationDtos.WeekUtilization;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TeamWeeklyActualHoursProjection;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.WeeklyActualHoursProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UtilizationService {

  private final ResourceAllocationRepository allocationRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final CapacityService capacityService;
  private final MemberRepository memberRepository;

  public UtilizationService(
      ResourceAllocationRepository allocationRepository,
      TimeEntryRepository timeEntryRepository,
      CapacityService capacityService,
      MemberRepository memberRepository) {
    this.allocationRepository = allocationRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.capacityService = capacityService;
    this.memberRepository = memberRepository;
  }

  /**
   * Calculates per-week utilization for a single member over a date range. For each week: resolves
   * effective capacity, queries planned hours (allocations) and actual hours (time entries), then
   * computes utilization percentages.
   */
  @Transactional(readOnly = true)
  public List<WeekUtilization> getMemberUtilization(
      UUID memberId, LocalDate weekStart, LocalDate weekEnd) {

    // Build lookup maps for planned and actual hours by week
    List<ResourceAllocation> allocations =
        allocationRepository.findByMemberIdAndWeekStartBetween(memberId, weekStart, weekEnd);
    Map<LocalDate, BigDecimal> plannedByWeek = new HashMap<>();
    for (ResourceAllocation alloc : allocations) {
      plannedByWeek.merge(alloc.getWeekStart(), alloc.getAllocatedHours(), BigDecimal::add);
    }

    List<WeeklyActualHoursProjection> actuals =
        timeEntryRepository.findWeeklyActualHours(
            memberId, weekStart, weekEnd.plusDays(6)); // include full last week
    Map<LocalDate, BigDecimal> actualByWeek = new HashMap<>();
    Map<LocalDate, BigDecimal> billableByWeek = new HashMap<>();
    for (WeeklyActualHoursProjection row : actuals) {
      actualByWeek.put(row.getWeekStart(), row.getActualHours());
      billableByWeek.put(row.getWeekStart(), row.getBillableHours());
    }

    // Iterate week by week
    List<WeekUtilization> weeks = new ArrayList<>();
    LocalDate current = weekStart;
    while (!current.isAfter(weekEnd)) {
      BigDecimal effectiveCapacity = capacityService.getMemberEffectiveCapacity(memberId, current);
      BigDecimal planned = plannedByWeek.getOrDefault(current, BigDecimal.ZERO);
      BigDecimal actual = actualByWeek.getOrDefault(current, BigDecimal.ZERO);
      BigDecimal billable = billableByWeek.getOrDefault(current, BigDecimal.ZERO);

      weeks.add(
          new WeekUtilization(
              current,
              effectiveCapacity,
              planned,
              actual,
              billable,
              pct(planned, effectiveCapacity),
              pct(actual, effectiveCapacity),
              pct(billable, effectiveCapacity)));
      current = current.plusWeeks(1);
    }
    return weeks;
  }

  /**
   * Calculates utilization for all org members over a date range. Returns per-member summaries with
   * weekly breakdowns plus team-level averages.
   */
  @Transactional(readOnly = true)
  public TeamUtilizationResponse getTeamUtilization(LocalDate weekStart, LocalDate weekEnd) {
    List<Member> members = memberRepository.findAll();

    // Batch-query team actual hours
    List<TeamWeeklyActualHoursProjection> teamActuals =
        timeEntryRepository.findTeamWeeklyActualHours(weekStart, weekEnd.plusDays(6));
    Map<UUID, Map<LocalDate, BigDecimal>> actualByMemberWeek = new HashMap<>();
    Map<UUID, Map<LocalDate, BigDecimal>> billableByMemberWeek = new HashMap<>();
    for (TeamWeeklyActualHoursProjection row : teamActuals) {
      actualByMemberWeek
          .computeIfAbsent(row.getMemberId(), k -> new HashMap<>())
          .put(row.getWeekStart(), row.getActualHours());
      billableByMemberWeek
          .computeIfAbsent(row.getMemberId(), k -> new HashMap<>())
          .put(row.getWeekStart(), row.getBillableHours());
    }

    // Batch-query allocations
    List<ResourceAllocation> allAllocations =
        allocationRepository.findByWeekStartBetween(weekStart, weekEnd);
    Map<UUID, Map<LocalDate, BigDecimal>> plannedByMemberWeek = new HashMap<>();
    for (ResourceAllocation alloc : allAllocations) {
      plannedByMemberWeek
          .computeIfAbsent(alloc.getMemberId(), k -> new HashMap<>())
          .put(
              alloc.getWeekStart(),
              plannedByMemberWeek
                  .computeIfAbsent(alloc.getMemberId(), k -> new HashMap<>())
                  .getOrDefault(alloc.getWeekStart(), BigDecimal.ZERO)
                  .add(alloc.getAllocatedHours()));
    }

    List<MemberUtilizationSummary> summaries = new ArrayList<>();
    BigDecimal totalPlannedPct = BigDecimal.ZERO;
    BigDecimal totalActualPct = BigDecimal.ZERO;
    BigDecimal totalBillablePct = BigDecimal.ZERO;

    for (Member member : members) {
      UUID memberId = member.getId();
      Map<LocalDate, BigDecimal> memberPlanned =
          plannedByMemberWeek.getOrDefault(memberId, Map.of());
      Map<LocalDate, BigDecimal> memberActual = actualByMemberWeek.getOrDefault(memberId, Map.of());
      Map<LocalDate, BigDecimal> memberBillable =
          billableByMemberWeek.getOrDefault(memberId, Map.of());

      List<WeekUtilization> weeks = new ArrayList<>();
      BigDecimal sumPlanned = BigDecimal.ZERO;
      BigDecimal sumActual = BigDecimal.ZERO;
      BigDecimal sumBillable = BigDecimal.ZERO;
      BigDecimal sumCapacity = BigDecimal.ZERO;
      int overAllocatedWeeks = 0;

      LocalDate current = weekStart;
      while (!current.isAfter(weekEnd)) {
        BigDecimal effectiveCapacity =
            capacityService.getMemberEffectiveCapacity(memberId, current);
        BigDecimal planned = memberPlanned.getOrDefault(current, BigDecimal.ZERO);
        BigDecimal actual = memberActual.getOrDefault(current, BigDecimal.ZERO);
        BigDecimal billable = memberBillable.getOrDefault(current, BigDecimal.ZERO);

        if (planned.compareTo(effectiveCapacity) > 0) {
          overAllocatedWeeks++;
        }

        weeks.add(
            new WeekUtilization(
                current,
                effectiveCapacity,
                planned,
                actual,
                billable,
                pct(planned, effectiveCapacity),
                pct(actual, effectiveCapacity),
                pct(billable, effectiveCapacity)));

        sumPlanned = sumPlanned.add(planned);
        sumActual = sumActual.add(actual);
        sumBillable = sumBillable.add(billable);
        sumCapacity = sumCapacity.add(effectiveCapacity);
        current = current.plusWeeks(1);
      }

      BigDecimal weeklyCapacity = capacityService.getMemberCapacity(memberId, weekStart);
      BigDecimal avgPlannedPct = pct(sumPlanned, sumCapacity);
      BigDecimal avgActualPct = pct(sumActual, sumCapacity);
      BigDecimal avgBillablePct = pct(sumBillable, sumCapacity);

      summaries.add(
          new MemberUtilizationSummary(
              memberId,
              member.getName(),
              weeklyCapacity,
              sumPlanned,
              sumActual,
              sumBillable,
              avgPlannedPct,
              avgActualPct,
              avgBillablePct,
              overAllocatedWeeks,
              weeks));

      totalPlannedPct = totalPlannedPct.add(avgPlannedPct);
      totalActualPct = totalActualPct.add(avgActualPct);
      totalBillablePct = totalBillablePct.add(avgBillablePct);
    }

    int memberCount = members.size();
    TeamAverages averages =
        memberCount > 0
            ? new TeamAverages(
                totalPlannedPct.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP),
                totalActualPct.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP),
                totalBillablePct.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP))
            : new TeamAverages(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    return new TeamUtilizationResponse(summaries, averages);
  }

  /** Calculates percentage = (numerator / denominator) * 100. Returns ZERO if denominator is 0. */
  private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
  }
}
