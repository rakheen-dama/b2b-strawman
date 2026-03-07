package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.budget.ProjectBudget;
import io.b2mash.b2b.b2bstrawman.budget.ProjectBudgetRepository;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.CreateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.MemberCapacityResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.UpdateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.AllocationSlot;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.MemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.ProjectStaffingResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.StaffingMemberRow;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.StaffingWeekCell;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.TeamCapacityGrid;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.WeekCell;
import io.b2mash.b2b.b2bstrawman.capacity.dto.GridDtos.WeekSummary;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

@Service
public class CapacityService {

  private static final BigDecimal HARD_DEFAULT_WEEKLY_HOURS = new BigDecimal("40.00");
  private static final int WORKING_DAYS_PER_WEEK = 5;

  private final MemberCapacityRepository memberCapacityRepository;
  private final LeaveBlockRepository leaveBlockRepository;
  private final OrgSettingsService orgSettingsService;
  private final MemberRepository memberRepository;
  private final ResourceAllocationRepository allocationRepository;
  private final ProjectRepository projectRepository;
  private final ProjectBudgetRepository projectBudgetRepository;

  public CapacityService(
      MemberCapacityRepository memberCapacityRepository,
      LeaveBlockRepository leaveBlockRepository,
      OrgSettingsService orgSettingsService,
      MemberRepository memberRepository,
      ResourceAllocationRepository allocationRepository,
      ProjectRepository projectRepository,
      ProjectBudgetRepository projectBudgetRepository) {
    this.memberCapacityRepository = memberCapacityRepository;
    this.leaveBlockRepository = leaveBlockRepository;
    this.orgSettingsService = orgSettingsService;
    this.memberRepository = memberRepository;
    this.allocationRepository = allocationRepository;
    this.projectRepository = projectRepository;
    this.projectBudgetRepository = projectBudgetRepository;
  }

  /**
   * Resolves the weekly capacity for a member on a given week. Resolution chain: 1. MemberCapacity
   * record (latest effectiveFrom wins) 2. OrgSettings.defaultWeeklyCapacityHours 3. Hard default
   * 40.0
   */
  @Transactional(readOnly = true)
  public BigDecimal getMemberCapacity(UUID memberId, LocalDate weekStart) {
    List<MemberCapacity> records =
        memberCapacityRepository.findEffectiveCapacity(memberId, weekStart);
    if (!records.isEmpty()) {
      return records.getFirst().getWeeklyHours();
    }

    // OrgSettingsService already falls back to 40.0, but this is a safety net
    BigDecimal orgDefault = orgSettingsService.getDefaultWeeklyCapacityHours();
    return orgDefault != null ? orgDefault : HARD_DEFAULT_WEEKLY_HOURS;
  }

  /**
   * Returns the effective capacity for a member in a given week, reduced by leave days.
   * effectiveCapacity = baseCapacity * (5 - leaveDays) / 5
   */
  @Transactional(readOnly = true)
  public BigDecimal getMemberEffectiveCapacity(UUID memberId, LocalDate weekStart) {
    BigDecimal baseCapacity = getMemberCapacity(memberId, weekStart);
    int leaveDays = countLeaveDaysInWeek(memberId, weekStart);
    int workingDays = WORKING_DAYS_PER_WEEK - leaveDays;
    if (workingDays <= 0) {
      return BigDecimal.ZERO;
    }
    return baseCapacity
        .multiply(BigDecimal.valueOf(workingDays))
        .divide(BigDecimal.valueOf(WORKING_DAYS_PER_WEEK), 2, RoundingMode.HALF_UP);
  }

  /** Lists all capacity records for a member, ordered by effectiveFrom DESC. */
  @Transactional(readOnly = true)
  public List<MemberCapacityResponse> listCapacityRecords(UUID memberId) {
    return memberCapacityRepository.findByMemberIdOrderByEffectiveFromDesc(memberId).stream()
        .map(this::toResponse)
        .toList();
  }

  /** Creates a new capacity record. effectiveFrom must be a Monday. weeklyHours must be > 0. */
  @Transactional
  public MemberCapacityResponse createCapacityRecord(
      UUID memberId, CreateCapacityRequest request, UUID createdBy) {
    validateEffectiveFrom(request.effectiveFrom());
    if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
      throw new InvalidStateException(
          "Invalid effective date", "effectiveTo must be after effectiveFrom");
    }

    var record =
        new MemberCapacity(
            memberId,
            request.weeklyHours(),
            request.effectiveFrom(),
            request.effectiveTo(),
            request.note(),
            createdBy);
    record = memberCapacityRepository.save(record);
    return toResponse(record);
  }

  /** Updates an existing capacity record. Validates that the record belongs to the given member. */
  @Transactional
  public MemberCapacityResponse updateCapacityRecord(
      UUID memberId, UUID id, UpdateCapacityRequest request) {
    var record =
        memberCapacityRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MemberCapacity", id));
    if (!record.getMemberId().equals(memberId)) {
      throw new ResourceNotFoundException("MemberCapacity", id);
    }

    record.update(
        request.weeklyHours(), record.getEffectiveFrom(), request.effectiveTo(), request.note());
    record = memberCapacityRepository.save(record);
    return toResponse(record);
  }

  /** Deletes a capacity record by ID. Validates that the record belongs to the given member. */
  @Transactional
  public void deleteCapacityRecord(UUID memberId, UUID id) {
    var record =
        memberCapacityRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MemberCapacity", id));
    if (!record.getMemberId().equals(memberId)) {
      throw new ResourceNotFoundException("MemberCapacity", id);
    }
    memberCapacityRepository.delete(record);
  }

  /**
   * Assembles the full team capacity grid: members x weeks with allocations, capacity, and
   * utilization. Uses 5 queries: members, allocations, leave blocks, capacity records, org
   * settings.
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

    // Index leave blocks by memberId
    Map<UUID, List<LeaveBlock>> leaveByMember = new HashMap<>();
    for (LeaveBlock lb : leaveBlocks) {
      leaveByMember.computeIfAbsent(lb.getMemberId(), k -> new ArrayList<>()).add(lb);
    }

    // Build week list
    List<LocalDate> weekStarts = new ArrayList<>();
    LocalDate current = weekStart;
    while (!current.isAfter(weekEnd)) {
      weekStarts.add(current);
      current = current.plusWeeks(1);
    }

    // Assemble member rows and week summaries
    Map<LocalDate, BigDecimal> teamAllocByWeek = new HashMap<>();
    Map<LocalDate, BigDecimal> teamCapByWeek = new HashMap<>();
    List<MemberRow> memberRows = new ArrayList<>();

    for (Member member : members) {
      UUID memberId = member.getId();
      Map<LocalDate, List<ResourceAllocation>> memberAllocs =
          allocsByMemberWeek.getOrDefault(memberId, Map.of());

      List<WeekCell> weekCells = new ArrayList<>();
      BigDecimal memberTotalAllocated = BigDecimal.ZERO;
      BigDecimal memberTotalCapacity = BigDecimal.ZERO;

      for (LocalDate ws : weekStarts) {
        BigDecimal effectiveCapacity = getMemberEffectiveCapacity(memberId, ws);
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
        int leaveDays = countLeaveDaysInWeek(memberId, ws);

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
      BigDecimal effectiveCapacity = getMemberEffectiveCapacity(memberId, current);
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
      int leaveDays = countLeaveDaysInWeek(memberId, current);

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

  /** Calculates percentage = (numerator / denominator) * 100. Returns ZERO if denominator is 0. */
  private BigDecimal pct(BigDecimal numerator, BigDecimal denominator) {
    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
  }

  private int countLeaveDaysInWeek(UUID memberId, LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusDays(4); // Monday to Friday
    List<LeaveBlock> overlappingBlocks =
        leaveBlockRepository.findByMemberIdAndOverlapping(memberId, weekStart, weekEnd);

    Set<LocalDate> leaveDays = new HashSet<>();
    for (LeaveBlock block : overlappingBlocks) {
      LocalDate day = block.getStartDate().isBefore(weekStart) ? weekStart : block.getStartDate();
      LocalDate end = block.getEndDate().isAfter(weekEnd) ? weekEnd : block.getEndDate();
      while (!day.isAfter(end)) {
        if (day.getDayOfWeek().getValue() <= 5) { // Mon=1 ... Fri=5
          leaveDays.add(day);
        }
        day = day.plusDays(1);
      }
    }
    return Math.min(leaveDays.size(), WORKING_DAYS_PER_WEEK);
  }

  private void validateEffectiveFrom(LocalDate effectiveFrom) {
    if (effectiveFrom.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new InvalidStateException("Invalid effective date", "effectiveFrom must be a Monday");
    }
  }

  private MemberCapacityResponse toResponse(MemberCapacity record) {
    return new MemberCapacityResponse(
        record.getId(),
        record.getMemberId(),
        record.getWeeklyHours(),
        record.getEffectiveFrom(),
        record.getEffectiveTo(),
        record.getNote(),
        record.getCreatedAt());
  }
}
