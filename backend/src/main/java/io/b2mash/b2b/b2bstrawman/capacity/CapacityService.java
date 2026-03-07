package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.CreateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.MemberCapacityResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.CapacityDtos.UpdateCapacityRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles member capacity CRUD and resolution. Grid assembly and staffing views are in {@link
 * CapacityGridService}.
 */
@Service
public class CapacityService {

  private static final BigDecimal HARD_DEFAULT_WEEKLY_HOURS = new BigDecimal("40.00");
  static final int WORKING_DAYS_PER_WEEK = 5;

  private final MemberCapacityRepository memberCapacityRepository;
  private final LeaveBlockRepository leaveBlockRepository;
  private final OrgSettingsService orgSettingsService;

  public CapacityService(
      MemberCapacityRepository memberCapacityRepository,
      LeaveBlockRepository leaveBlockRepository,
      OrgSettingsService orgSettingsService) {
    this.memberCapacityRepository = memberCapacityRepository;
    this.leaveBlockRepository = leaveBlockRepository;
    this.orgSettingsService = orgSettingsService;
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
    return getOrgDefaultCapacity();
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

  /** Returns the org-level default weekly capacity, falling back to 40.0 if not configured. */
  BigDecimal getOrgDefaultCapacity() {
    BigDecimal orgDefault = orgSettingsService.getDefaultWeeklyCapacityHours();
    return orgDefault != null ? orgDefault : HARD_DEFAULT_WEEKLY_HOURS;
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

  /** Counts leave days in a given week by querying the database. Package-private for reuse. */
  int countLeaveDaysInWeek(UUID memberId, LocalDate weekStart) {
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
