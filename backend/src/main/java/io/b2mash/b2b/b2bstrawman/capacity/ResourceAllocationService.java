package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResultItem;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.BulkAllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.UpdateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceAllocationService {

  private static final BigDecimal MAX_WEEKLY_HOURS = new BigDecimal("168");

  private final ResourceAllocationRepository allocationRepository;
  private final ProjectRepository projectRepository;
  private final ProjectMemberService projectMemberService;
  private final CapacityService capacityService;
  private final ApplicationEventPublisher eventPublisher;

  public ResourceAllocationService(
      ResourceAllocationRepository allocationRepository,
      ProjectRepository projectRepository,
      ProjectMemberService projectMemberService,
      CapacityService capacityService,
      ApplicationEventPublisher eventPublisher) {
    this.allocationRepository = allocationRepository;
    this.projectRepository = projectRepository;
    this.projectMemberService = projectMemberService;
    this.capacityService = capacityService;
    this.eventPublisher = eventPublisher;
  }

  /** Lists allocations with optional filters: memberId, projectId, weekStart, weekEnd. */
  @Transactional(readOnly = true)
  public List<AllocationResponse> listAllocations(
      UUID memberId, UUID projectId, LocalDate weekStart, LocalDate weekEnd) {
    List<ResourceAllocation> allocations;

    if (memberId != null && weekStart != null && weekEnd != null) {
      allocations =
          allocationRepository.findByMemberIdAndWeekStartBetween(memberId, weekStart, weekEnd);
    } else if (projectId != null && weekStart != null && weekEnd != null) {
      allocations =
          allocationRepository.findByProjectIdAndWeekStartBetween(projectId, weekStart, weekEnd);
    } else if (weekStart != null && weekEnd != null) {
      allocations = allocationRepository.findByWeekStartBetween(weekStart, weekEnd);
    } else {
      allocations = allocationRepository.findAll();
    }

    return allocations.stream().map(a -> toResponse(a, false, BigDecimal.ZERO)).toList();
  }

  /** Creates a new resource allocation with validation, auto-add, and over-allocation check. */
  @Transactional
  public AllocationResponse createAllocation(CreateAllocationRequest request, UUID createdBy) {
    validateWeekStart(request.weekStart());
    validateProjectNotArchivedOrCompleted(request.projectId());
    validateAllocatedHours(request.allocatedHours());
    checkUniqueness(request.memberId(), request.projectId(), request.weekStart());
    autoAddProjectMember(request.projectId(), request.memberId(), createdBy);

    var allocation =
        new ResourceAllocation(
            request.memberId(),
            request.projectId(),
            request.weekStart(),
            request.allocatedHours(),
            request.note(),
            createdBy);
    allocation = allocationRepository.save(allocation);

    return buildResponseWithOverAllocationCheck(allocation);
  }

  /** Updates an existing allocation's hours and note, then re-checks over-allocation. */
  @Transactional
  public AllocationResponse updateAllocation(UUID id, UpdateAllocationRequest request) {
    validateAllocatedHours(request.allocatedHours());

    var allocation =
        allocationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ResourceAllocation", id));

    allocation.update(request.allocatedHours(), request.note());
    allocation = allocationRepository.save(allocation);

    return buildResponseWithOverAllocationCheck(allocation);
  }

  /** Deletes an allocation by ID. */
  @Transactional
  public void deleteAllocation(UUID id) {
    var allocation =
        allocationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ResourceAllocation", id));
    allocationRepository.delete(allocation);
  }

  /**
   * Bulk upsert allocations. Creates or updates each item, deduplicates over-allocation checks per
   * unique (memberId, weekStart) pair.
   */
  @Transactional
  public BulkAllocationResponse bulkUpsertAllocations(
      List<CreateAllocationRequest> requests, UUID createdBy) {
    List<AllocationResultItem> results = new ArrayList<>();
    Set<MemberWeekKey> memberWeekKeys = new LinkedHashSet<>();

    // Phase 1: process all items (create or update)
    for (CreateAllocationRequest request : requests) {
      validateWeekStart(request.weekStart());
      validateProjectNotArchivedOrCompleted(request.projectId());
      validateAllocatedHours(request.allocatedHours());

      var existing =
          allocationRepository.findByMemberIdAndProjectIdAndWeekStart(
              request.memberId(), request.projectId(), request.weekStart());

      boolean created;
      ResourceAllocation allocation;
      if (existing.isPresent()) {
        allocation = existing.get();
        allocation.update(request.allocatedHours(), request.note());
        allocation = allocationRepository.save(allocation);
        created = false;
      } else {
        autoAddProjectMember(request.projectId(), request.memberId(), createdBy);
        allocation =
            new ResourceAllocation(
                request.memberId(),
                request.projectId(),
                request.weekStart(),
                request.allocatedHours(),
                request.note(),
                createdBy);
        allocation = allocationRepository.save(allocation);
        created = true;
      }

      // Placeholder response; over-allocation will be filled in phase 2
      results.add(
          new AllocationResultItem(toResponse(allocation, false, BigDecimal.ZERO), created));
      memberWeekKeys.add(new MemberWeekKey(request.memberId(), request.weekStart()));
    }

    // Phase 2: deduplicated over-allocation checks
    Map<MemberWeekKey, OverAllocationResult> overAllocationMap = new LinkedHashMap<>();
    for (MemberWeekKey key : memberWeekKeys) {
      overAllocationMap.put(key, checkOverAllocation(key.memberId(), key.weekStart()));
    }

    // Phase 3: apply over-allocation results to each item
    List<AllocationResultItem> finalResults = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      AllocationResultItem item = results.get(i);
      CreateAllocationRequest request = requests.get(i);
      MemberWeekKey key = new MemberWeekKey(request.memberId(), request.weekStart());
      OverAllocationResult oar = overAllocationMap.get(key);

      AllocationResponse updatedResponse =
          new AllocationResponse(
              item.allocation().id(),
              item.allocation().memberId(),
              item.allocation().projectId(),
              item.allocation().weekStart(),
              item.allocation().allocatedHours(),
              item.allocation().note(),
              oar.overAllocated(),
              oar.overageHours(),
              item.allocation().createdAt());
      finalResults.add(new AllocationResultItem(updatedResponse, item.created()));
    }

    return new BulkAllocationResponse(finalResults);
  }

  // --- Private helpers ---

  private void validateWeekStart(LocalDate weekStart) {
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new InvalidStateException("Invalid week start", "weekStart must be a Monday");
    }
  }

  private void validateProjectNotArchivedOrCompleted(UUID projectId) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    if (project.getStatus() == ProjectStatus.ARCHIVED
        || project.getStatus() == ProjectStatus.COMPLETED) {
      throw new InvalidStateException(
          "Project not active", "Cannot allocate to a project with status " + project.getStatus());
    }
  }

  private void validateAllocatedHours(BigDecimal allocatedHours) {
    if (allocatedHours.compareTo(BigDecimal.ZERO) <= 0
        || allocatedHours.compareTo(MAX_WEEKLY_HOURS) > 0) {
      throw new InvalidStateException(
          "Invalid allocated hours", "allocatedHours must be > 0 and <= 168");
    }
  }

  private void checkUniqueness(UUID memberId, UUID projectId, LocalDate weekStart) {
    allocationRepository
        .findByMemberIdAndProjectIdAndWeekStart(memberId, projectId, weekStart)
        .ifPresent(
            existing -> {
              throw new ResourceConflictException(
                  "Duplicate allocation",
                  "Allocation already exists for member "
                      + memberId
                      + " on project "
                      + projectId
                      + " for week "
                      + weekStart);
            });
  }

  private void autoAddProjectMember(UUID projectId, UUID memberId, UUID addedBy) {
    if (!projectMemberService.isProjectMember(projectId, memberId)) {
      projectMemberService.addMember(projectId, memberId, addedBy);
    }
  }

  private AllocationResponse buildResponseWithOverAllocationCheck(ResourceAllocation allocation) {
    OverAllocationResult oar =
        checkOverAllocation(allocation.getMemberId(), allocation.getWeekStart());
    return toResponse(allocation, oar.overAllocated(), oar.overageHours());
  }

  private OverAllocationResult checkOverAllocation(UUID memberId, LocalDate weekStart) {
    BigDecimal totalAllocated =
        allocationRepository.sumAllocatedHoursForMemberWeek(memberId, weekStart);
    BigDecimal effectiveCapacity = capacityService.getMemberEffectiveCapacity(memberId, weekStart);

    if (totalAllocated.compareTo(effectiveCapacity) > 0) {
      BigDecimal overageHours = totalAllocated.subtract(effectiveCapacity);
      eventPublisher.publishEvent(
          new MemberOverAllocatedEvent(
              memberId, weekStart, totalAllocated, effectiveCapacity, overageHours));
      return new OverAllocationResult(true, overageHours);
    }
    return new OverAllocationResult(false, BigDecimal.ZERO);
  }

  private AllocationResponse toResponse(
      ResourceAllocation allocation, boolean overAllocated, BigDecimal overageHours) {
    return new AllocationResponse(
        allocation.getId(),
        allocation.getMemberId(),
        allocation.getProjectId(),
        allocation.getWeekStart(),
        allocation.getAllocatedHours(),
        allocation.getNote(),
        overAllocated,
        overageHours,
        allocation.getCreatedAt());
  }

  private record MemberWeekKey(UUID memberId, LocalDate weekStart) {}

  private record OverAllocationResult(boolean overAllocated, BigDecimal overageHours) {}
}
