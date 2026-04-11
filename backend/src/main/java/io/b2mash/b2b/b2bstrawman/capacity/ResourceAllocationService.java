package io.b2mash.b2b.b2bstrawman.capacity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.AllocationResultItem;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.BulkAllocationResponse;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.CreateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.capacity.dto.AllocationDtos.UpdateAllocationRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectStatus;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
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

  private static final String MODULE_ID = "resource_planning";
  private static final BigDecimal MAX_WEEKLY_HOURS = new BigDecimal("168");

  private final ResourceAllocationRepository allocationRepository;
  private final ProjectRepository projectRepository;
  private final ProjectMemberService projectMemberService;
  private final CapacityService capacityService;
  private final ApplicationEventPublisher eventPublisher;
  private final NotificationService notificationService;
  private final AuditService auditService;
  private final MemberRepository memberRepository;
  private final VerticalModuleGuard moduleGuard;

  public ResourceAllocationService(
      ResourceAllocationRepository allocationRepository,
      ProjectRepository projectRepository,
      ProjectMemberService projectMemberService,
      CapacityService capacityService,
      ApplicationEventPublisher eventPublisher,
      NotificationService notificationService,
      AuditService auditService,
      MemberRepository memberRepository,
      VerticalModuleGuard moduleGuard) {
    this.allocationRepository = allocationRepository;
    this.projectRepository = projectRepository;
    this.projectMemberService = projectMemberService;
    this.capacityService = capacityService;
    this.eventPublisher = eventPublisher;
    this.notificationService = notificationService;
    this.auditService = auditService;
    this.memberRepository = memberRepository;
    this.moduleGuard = moduleGuard;
  }

  /** Lists allocations with optional filters: memberId, projectId, weekStart, weekEnd. */
  @Transactional(readOnly = true)
  public List<AllocationResponse> listAllocations(
      UUID memberId, UUID projectId, LocalDate weekStart, LocalDate weekEnd) {
    moduleGuard.requireModule(MODULE_ID);

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
      // Default to current week through 12 weeks out to avoid unbounded queries
      LocalDate defaultStart = LocalDate.now().with(DayOfWeek.MONDAY);
      LocalDate defaultEnd = defaultStart.plusWeeks(12);
      allocations = allocationRepository.findByWeekStartBetween(defaultStart, defaultEnd);
    }

    return allocations.stream().map(a -> toResponse(a, false, BigDecimal.ZERO)).toList();
  }

  /** Creates a new resource allocation with validation, auto-add, and over-allocation check. */
  @Transactional
  public AllocationResponse createAllocation(CreateAllocationRequest request, UUID createdBy) {
    moduleGuard.requireModule(MODULE_ID);

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

    // Notification: ALLOCATION_CHANGED (only if allocating someone else)
    if (!createdBy.equals(request.memberId())) {
      String projectName = getProjectName(request.projectId());
      notificationService.createIfEnabled(
          request.memberId(),
          "ALLOCATION_CHANGED",
          "Allocation: %s — %s, %sh"
              .formatted(projectName, request.weekStart(), request.allocatedHours()),
          null,
          "RESOURCE_ALLOCATION",
          allocation.getId(),
          request.projectId());
    }

    // Audit event: resource_allocation.created
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("resource_allocation.created")
            .entityType("resource_allocation")
            .entityId(allocation.getId())
            .details(
                Map.of(
                    "member_id", request.memberId().toString(),
                    "project_id", request.projectId().toString(),
                    "week_start", request.weekStart().toString(),
                    "allocated_hours", request.allocatedHours().toString()))
            .build());

    return buildResponseWithOverAllocationCheck(allocation);
  }

  /** Updates an existing allocation's hours and note, then re-checks over-allocation. */
  @Transactional
  public AllocationResponse updateAllocation(UUID id, UpdateAllocationRequest request) {
    moduleGuard.requireModule(MODULE_ID);

    validateAllocatedHours(request.allocatedHours());

    var allocation =
        allocationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ResourceAllocation", id));

    BigDecimal oldHours = allocation.getAllocatedHours();
    allocation.update(request.allocatedHours(), request.note());
    allocation = allocationRepository.save(allocation);

    // Notification: ALLOCATION_CHANGED (only if updating someone else's allocation)
    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    if (actorId != null && !actorId.equals(allocation.getMemberId())) {
      String projectName = getProjectName(allocation.getProjectId());
      notificationService.createIfEnabled(
          allocation.getMemberId(),
          "ALLOCATION_CHANGED",
          "Allocation updated: %s — %s, %sh"
              .formatted(projectName, allocation.getWeekStart(), request.allocatedHours()),
          null,
          "RESOURCE_ALLOCATION",
          allocation.getId(),
          allocation.getProjectId());
    }

    // Audit event: resource_allocation.updated
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("resource_allocation.updated")
            .entityType("resource_allocation")
            .entityId(allocation.getId())
            .details(
                Map.of(
                    "member_id", allocation.getMemberId().toString(),
                    "project_id", allocation.getProjectId().toString(),
                    "week_start", allocation.getWeekStart().toString(),
                    "old_hours", oldHours.toString(),
                    "new_hours", request.allocatedHours().toString()))
            .build());

    return buildResponseWithOverAllocationCheck(allocation);
  }

  /** Deletes an allocation by ID. */
  @Transactional
  public void deleteAllocation(UUID id) {
    moduleGuard.requireModule(MODULE_ID);

    var allocation =
        allocationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ResourceAllocation", id));

    // Audit event: resource_allocation.deleted (capture before delete)
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("resource_allocation.deleted")
            .entityType("resource_allocation")
            .entityId(allocation.getId())
            .details(
                Map.of(
                    "member_id", allocation.getMemberId().toString(),
                    "project_id", allocation.getProjectId().toString(),
                    "week_start", allocation.getWeekStart().toString(),
                    "allocated_hours", allocation.getAllocatedHours().toString()))
            .build());

    allocationRepository.delete(allocation);
  }

  /**
   * Bulk upsert allocations. Creates or updates each item, deduplicates over-allocation checks per
   * unique (memberId, weekStart) pair.
   */
  @Transactional
  public BulkAllocationResponse bulkUpsertAllocations(
      List<CreateAllocationRequest> requests, UUID createdBy) {
    moduleGuard.requireModule(MODULE_ID);

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
      try {
        projectMemberService.addMember(projectId, memberId, addedBy);
      } catch (ResourceConflictException e) {
        // Another request already added the member — safe to ignore (idempotent add)
      }
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

      // Notification: MEMBER_OVER_ALLOCATED to the member + admins/owners
      sendOverAllocationNotifications(memberId, weekStart, overageHours);

      return new OverAllocationResult(true, overageHours);
    }
    return new OverAllocationResult(false, BigDecimal.ZERO);
  }

  private void sendOverAllocationNotifications(
      UUID memberId, LocalDate weekStart, BigDecimal overageHours) {
    String memberName = memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
    String title =
        "Over-allocated: %s — week of %s, %sh over capacity"
            .formatted(memberName, weekStart, overageHours);

    UUID actorId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;

    // Collect unique recipients: the member + admins/owners, excluding actor
    Set<UUID> recipients = new HashSet<>();
    recipients.add(memberId);
    var adminsAndOwners = memberRepository.findByRoleSlugsIn(List.of("admin", "owner"));
    for (var admin : adminsAndOwners) {
      recipients.add(admin.getId());
    }
    if (actorId != null) {
      recipients.remove(actorId);
    }

    for (UUID recipientId : recipients) {
      notificationService.createIfEnabled(
          recipientId, "MEMBER_OVER_ALLOCATED", title, null, "MEMBER", memberId, null);
    }
  }

  private String getProjectName(UUID projectId) {
    return projectRepository.findById(projectId).map(p -> p.getName()).orElse("Unknown Project");
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
