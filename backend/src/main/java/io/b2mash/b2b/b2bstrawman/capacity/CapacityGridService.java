package io.b2mash.b2b.b2bstrawman.capacity;

import static io.b2mash.b2b.b2bstrawman.capacity.CapacityMathUtil.pct;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.AllocationSlot;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.MemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.ProjectStaffingResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.StaffingMemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.StaffingWeekCell;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.TeamCapacityGrid;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.WeekCell;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.WeekSummary;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles capacity grid and staffing views. Separated from {@link CapacityService} (CRUD +
 * resolution) to keep dependency counts manageable.
 */
@Service
public class CapacityGridService {

  private static final int WORKING_DAYS_PER_WEEK = 5;

  private final CapacityService capacityService;
  private final MemberRepository memberRepository;
  private final ResourceAllocationRepository allocationRepository;
  private final ProjectRepository projectRepository;
  private final ProjectBudgetRepository projectBudgetRepository;
  private final LeaveBlockRepository leaveBlockRepository;
  private final MemberCapacityRepository memberCapacityRepository;

  public CapacityGridService(
      CapacityService capacityService,
      MemberRepository memberRepository,
      ResourceAllocationRepository allocationRepository,
      ProjectRepository projectRepository,
      ProjectBudgetRepository projectBudgetRepository,
      LeaveBlockRepository leaveBlockRepository,
      MemberCapacityRepository memberCapacityRepository) {
    this.capacityService = capacityService;
    this.memberRepository = memberRepository;
    this.allocationRepository = allocationRepository;
    this.projectRepository = projectRepository;
    this.projectBudgetRepository = projectBudgetRepository;
    this.leaveBlockRepository = leaveBlockRepository;
    this.memberCapacityRepository = memberCapacityRepository;
  }

  /**
   * Assembles the full team capacity grid: members x weeks with allocations, capacity, and
   * utilization. Uses batch queries for members, allocations, leave blocks, capacity records, and
   * org settings -- no per-cell DB calls.
   */
  @Transactional(readOnly = true)
  public TeamCapacityGrid getTeamCapacityGrid(LocalDate weekStart, LocalDate weekEnd) {
    List<Member> members = memberRepository.findAll();
    List<ResourceAllocation> allocations =
        allocationRepository.findByWeekStartBetween(weekStart, weekEnd);
    List<LeaveBlock> leaveBlocks = leaveBlockRepository.findAllOverlapping(weekStart, weekEnd);

    // Build project name lookup from allocations
    Set<UUID> projectIds =
        allocations.stream().map(ResourceAllocation::getProjectId).collect(Collectors.toSet());
    Map<UUID, String> projectNames = new HashMap<>();
    if (!projectIds.isEmpty()) {
      projectRepository
          .findAllById(projectIds)
          .forEach(p -> projectNames.put(p.getId(), p.getName()));
    }

    // Index allocations by memberId+weekStart
    Map<UUID, Map<LocalDate, List<ResourceAllocation>>> allocsByMemberWeek = new HashMap<>();
    for (ResourceAllocation alloc : allocations) {
      allocsByMemberWeek
          .computeIfAbsent(alloc.getMemberId(), k -> new HashMap<>())
          .computeIfAbsent(alloc.getWeekStart(), k -> new ArrayList<>())
          .add(alloc);
    }

    // Index leave blocks by memberId for in-memory leave-day computation
    Map<UUID, List<LeaveBlock>> leaveByMember = new HashMap<>();
    for (LeaveBlock lb : leaveBlocks) {
      leaveByMember.computeIfAbsent(lb.getMemberId(), k -> new ArrayList<>()).add(lb);
    }

    // Batch-fetch capacity records for in-memory resolution
    List<MemberCapacity> allCapacityRecords =
        memberCapacityRepository.findAllEffectiveInRange(weekStart, weekEnd);
    Map<UUID, List<MemberCapacity>> capacityByMember = new HashMap<>();
    for (MemberCapacity mc : allCapacityRecords) {
      capacityByMember.computeIfAbsent(mc.getMemberId(), k -> new ArrayList<>()).add(mc);
    }

    // Build week list
    List<LocalDate> weekStarts = new ArrayList<>();
    LocalDate current = weekStart;
    while (!current.isAfter(weekEnd)) {
      weekStarts.add(current);
      current = current.plusWeeks(1);
    }

    BigDecimal orgDefault = capacityService.getOrgDefaultCapacity();

    // Assemble member rows and week summaries
    Map<LocalDate, BigDecimal> teamAllocByWeek = new HashMap<>();
    Map<LocalDate, BigDecimal> teamCapByWeek = new HashMap<>();
    List<MemberRow> memberRows = new ArrayList<>();

    for (Member member : members) {
      UUID memberId = member.getId();
      Map<LocalDate, List<ResourceAllocation>> memberAllocs =
          allocsByMemberWeek.getOrDefault(memberId, Map.of());
      List<LeaveBlock> memberLeave = leaveByMember.getOrDefault(memberId, List.of());
      List<MemberCapacity> memberCapRecords = capacityByMember.getOrDefault(memberId, List.of());

      List<WeekCell> weekCells = new ArrayList<>();
      BigDecimal memberTotalAllocated = BigDecimal.ZERO;
      BigDecimal memberTotalCapacity = BigDecimal.ZERO;

      for (LocalDate ws : weekStarts) {
        BigDecimal baseCapacity = resolveCapacityInMemory(memberCapRecords, ws, orgDefault);
        int leaveDays = countLeaveDaysInMemory(memberLeave, ws);
        BigDecimal effectiveCapacity = computeEffectiveCapacity(baseCapacity, leaveDays);

        List<ResourceAllocation> weekAllocs = memberAllocs.getOrDefault(ws, List.of());

        List<AllocationSlot> slots = new ArrayList<>();
        BigDecimal totalAllocated = BigDecimal.ZERO;
        for (ResourceAllocation alloc : weekAllocs) {
          slots.add(
              new AllocationSlot(
                  alloc.getProjectId(),
                  projectNames.getOrDefault(alloc.getProjectId(), "Unknown"),
                  alloc.getAllocatedHours()));
          totalAllocated = totalAllocated.add(alloc.getAllocatedHours());
        }

        BigDecimal remaining = effectiveCapacity.subtract(totalAllocated);
        BigDecimal utilizationPct = pct(totalAllocated, effectiveCapacity);
        boolean overAllocated = totalAllocated.compareTo(effectiveCapacity) > 0;

        weekCells.add(
            new WeekCell(
                ws,
                slots,
                totalAllocated,
                effectiveCapacity,
                remaining,
                utilizationPct,
                overAllocated,
                leaveDays));

        memberTotalAllocated = memberTotalAllocated.add(totalAllocated);
        memberTotalCapacity = memberTotalCapacity.add(effectiveCapacity);
        teamAllocByWeek.merge(ws, totalAllocated, BigDecimal::add);
        teamCapByWeek.merge(ws, effectiveCapacity, BigDecimal::add);
      }

      memberRows.add(
          new MemberRow(
              memberId,
              member.getName(),
              member.getAvatarUrl(),
              weekCells,
              memberTotalAllocated,
              memberTotalCapacity,
              pct(memberTotalAllocated, memberTotalCapacity)));
    }

    List<WeekSummary> weekSummaries = new ArrayList<>();
    for (LocalDate ws : weekStarts) {
      BigDecimal teamAlloc = teamAllocByWeek.getOrDefault(ws, BigDecimal.ZERO);
      BigDecimal teamCap = teamCapByWeek.getOrDefault(ws, BigDecimal.ZERO);
      weekSummaries.add(new WeekSummary(ws, teamAlloc, teamCap, pct(teamAlloc, teamCap)));
    }

    return new TeamCapacityGrid(memberRows, weekSummaries);
  }

  /**
   * Returns capacity detail for a single member (same structure as a MemberRow from the team grid).
   */
  @Transactional(readOnly = true)
  public MemberRow getMemberCapacityDetail(UUID memberId, LocalDate weekStart, LocalDate weekEnd) {
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

    List<ResourceAllocation> allocations =
        allocationRepository.findByMemberIdAndWeekStartBetween(memberId, weekStart, weekEnd);

    // Build project name lookup
    Set<UUID> projectIds =
        allocations.stream().map(ResourceAllocation::getProjectId).collect(Collectors.toSet());
    Map<UUID, String> projectNames = new HashMap<>();
    if (!projectIds.isEmpty()) {
      projectRepository
          .findAllById(projectIds)
          .forEach(p -> projectNames.put(p.getId(), p.getName()));
    }

    // Index allocations by week
    Map<LocalDate, List<ResourceAllocation>> allocsByWeek = new HashMap<>();
    for (ResourceAllocation alloc : allocations) {
      allocsByWeek.computeIfAbsent(alloc.getWeekStart(), k -> new ArrayList<>()).add(alloc);
    }

    List<WeekCell> weekCells = new ArrayList<>();
    BigDecimal totalAllocated = BigDecimal.ZERO;
    BigDecimal totalCapacity = BigDecimal.ZERO;

    LocalDate current = weekStart;
    while (!current.isAfter(weekEnd)) {
      BigDecimal effectiveCapacity = capacityService.getMemberEffectiveCapacity(memberId, current);
      List<ResourceAllocation> weekAllocs = allocsByWeek.getOrDefault(current, List.of());

      List<AllocationSlot> slots = new ArrayList<>();
      BigDecimal weekAllocated = BigDecimal.ZERO;
      for (ResourceAllocation alloc : weekAllocs) {
        slots.add(
            new AllocationSlot(
                alloc.getProjectId(),
                projectNames.getOrDefault(alloc.getProjectId(), "Unknown"),
                alloc.getAllocatedHours()));
        weekAllocated = weekAllocated.add(alloc.getAllocatedHours());
      }

      BigDecimal remaining = effectiveCapacity.subtract(weekAllocated);
      int leaveDays = capacityService.countLeaveDaysInWeek(memberId, current);

      weekCells.add(
          new WeekCell(
              current,
              slots,
              weekAllocated,
              effectiveCapacity,
              remaining,
              pct(weekAllocated, effectiveCapacity),
              weekAllocated.compareTo(effectiveCapacity) > 0,
              leaveDays));

      totalAllocated = totalAllocated.add(weekAllocated);
      totalCapacity = totalCapacity.add(effectiveCapacity);
      current = current.plusWeeks(1);
    }

    return new MemberRow(
        memberId,
        member.getName(),
        member.getAvatarUrl(),
        weekCells,
        totalAllocated,
        totalCapacity,
        pct(totalAllocated, totalCapacity));
  }

  /**
   * Returns staffing view for a project: allocated members with weekly breakdown and budget
   * comparison.
   */
  @Transactional(readOnly = true)
  public ProjectStaffingResponse getProjectStaffing(
      UUID projectId, LocalDate weekStart, LocalDate weekEnd) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    List<ResourceAllocation> allocations =
        allocationRepository.findByProjectIdAndWeekStartBetween(projectId, weekStart, weekEnd);

    // Group allocations by member
    Map<UUID, List<ResourceAllocation>> allocsByMember = new HashMap<>();
    for (ResourceAllocation alloc : allocations) {
      allocsByMember.computeIfAbsent(alloc.getMemberId(), k -> new ArrayList<>()).add(alloc);
    }

    // Build member name lookup
    Map<UUID, String> memberNames = new HashMap<>();
    if (!allocsByMember.isEmpty()) {
      memberRepository
          .findAllById(allocsByMember.keySet())
          .forEach(m -> memberNames.put(m.getId(), m.getName()));
    }

    BigDecimal totalPlannedHours = BigDecimal.ZERO;
    List<StaffingMemberRow> staffingMembers = new ArrayList<>();

    for (Map.Entry<UUID, List<ResourceAllocation>> entry : allocsByMember.entrySet()) {
      UUID memberId = entry.getKey();
      List<ResourceAllocation> memberAllocs = entry.getValue();

      List<StaffingWeekCell> weeks = new ArrayList<>();
      BigDecimal memberTotal = BigDecimal.ZERO;
      for (ResourceAllocation alloc : memberAllocs) {
        weeks.add(new StaffingWeekCell(alloc.getWeekStart(), alloc.getAllocatedHours()));
        memberTotal = memberTotal.add(alloc.getAllocatedHours());
      }

      staffingMembers.add(
          new StaffingMemberRow(
              memberId, memberNames.getOrDefault(memberId, "Unknown"), weeks, memberTotal));
      totalPlannedHours = totalPlannedHours.add(memberTotal);
    }

    // Budget comparison
    BigDecimal budgetHours = null;
    BigDecimal budgetUsedPct = null;
    var budgetOpt = projectBudgetRepository.findByProjectId(projectId);
    if (budgetOpt.isPresent()) {
      ProjectBudget budget = budgetOpt.get();
      if (budget.getBudgetHours() != null
          && budget.getBudgetHours().compareTo(BigDecimal.ZERO) > 0) {
        budgetHours = budget.getBudgetHours();
        budgetUsedPct = pct(totalPlannedHours, budgetHours);
      }
    }

    return new ProjectStaffingResponse(
        projectId,
        project.getName(),
        staffingMembers,
        totalPlannedHours,
        budgetHours,
        budgetUsedPct);
  }

  // --- In-memory helpers for batch-queried data ---

  /**
   * Resolves capacity from pre-fetched records. Returns the latest effectiveFrom that is <= the
   * given week date, falling back to org default.
   */
  private BigDecimal resolveCapacityInMemory(
      List<MemberCapacity> records, LocalDate weekStart, BigDecimal orgDefault) {
    // Records are ordered by effectiveFrom DESC from the repository query
    for (MemberCapacity mc : records) {
      if (!mc.getEffectiveFrom().isAfter(weekStart)
          && (mc.getEffectiveTo() == null || !mc.getEffectiveTo().isBefore(weekStart))) {
        return mc.getWeeklyHours();
      }
    }
    return orgDefault;
  }

  /** Counts leave days in a given week from pre-fetched leave blocks (no DB call). */
  private int countLeaveDaysInMemory(List<LeaveBlock> memberLeave, LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusDays(4); // Monday to Friday
    Set<LocalDate> leaveDays = new HashSet<>();
    for (LeaveBlock block : memberLeave) {
      if (block.getStartDate().isAfter(weekEnd) || block.getEndDate().isBefore(weekStart)) {
        continue; // no overlap with this week
      }
      LocalDate day = block.getStartDate().isBefore(weekStart) ? weekStart : block.getStartDate();
      LocalDate end = block.getEndDate().isAfter(weekEnd) ? weekEnd : block.getEndDate();
      while (!day.isAfter(end)) {
        if (day.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue()) {
          leaveDays.add(day);
        }
        day = day.plusDays(1);
      }
    }
    return Math.min(leaveDays.size(), WORKING_DAYS_PER_WEEK);
  }

  private BigDecimal computeEffectiveCapacity(BigDecimal baseCapacity, int leaveDays) {
    int workingDays = WORKING_DAYS_PER_WEEK - leaveDays;
    if (workingDays <= 0) {
      return BigDecimal.ZERO;
    }
    return baseCapacity
        .multiply(BigDecimal.valueOf(workingDays))
        .divide(BigDecimal.valueOf(WORKING_DAYS_PER_WEEK), 2, java.math.RoundingMode.HALF_UP);
  }
}
