package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskCancelledEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskCompletedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskRecurrenceCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskReopenedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private static final Logger log = LoggerFactory.getLogger(TaskService.class);

  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final ProjectMemberRepository projectMemberRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;
  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final FieldGroupService fieldGroupService;
  private final ProjectRepository projectRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ProjectLifecycleGuard projectLifecycleGuard;

  public TaskService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      ProjectMemberRepository projectMemberRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      CustomFieldValidator customFieldValidator,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      CustomerProjectRepository customerProjectRepository,
      FieldGroupService fieldGroupService,
      ProjectRepository projectRepository,
      TimeEntryRepository timeEntryRepository,
      ProjectLifecycleGuard projectLifecycleGuard) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.projectMemberRepository = projectMemberRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.fieldGroupService = fieldGroupService;
    this.projectRepository = projectRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.projectLifecycleGuard = projectLifecycleGuard;
  }

  private static final List<TaskStatus> DEFAULT_STATUSES =
      List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS);

  @Transactional(readOnly = true)
  public List<Task> listTasks(
      UUID projectId,
      UUID memberId,
      String orgRole,
      String status,
      UUID assigneeId,
      String priority,
      String assigneeFilter) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    List<TaskStatus> statuses = status != null ? parseStatuses(status) : DEFAULT_STATUSES;
    TaskPriority taskPriority = priority != null ? parsePriority(priority) : null;

    // Handle special "unassigned" filter
    if ("unassigned".equals(assigneeFilter)) {
      return taskRepository.findByProjectIdUnassigned(projectId, statuses, taskPriority);
    }

    return taskRepository.findByProjectIdWithFilters(projectId, statuses, assigneeId, taskPriority);
  }

  @Transactional(readOnly = true)
  public Task getTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);
    return task;
  }

  @Transactional
  public Task createTask(
      UUID projectId,
      String title,
      String description,
      String priority,
      String type,
      LocalDate dueDate,
      UUID createdBy,
      String orgRole) {
    return createTask(
        projectId,
        title,
        description,
        priority,
        type,
        dueDate,
        createdBy,
        orgRole,
        null,
        null,
        null);
  }

  /**
   * Creates a task, optionally pre-assigning it to a member at creation time.
   *
   * <p><strong>Permission asymmetry</strong>: pre-assignment at creation is a privileged action
   * (admin/owner only). This is intentional — assigning someone to a task before they have a chance
   * to claim it is an administrative decision. By contrast, re-assignment during the task lifecycle
   * (via {@link #updateTask}) is more permissive: the current assignee or any project lead can
   * change the assignee, reflecting the collaborative nature of ongoing work. Regular members who
   * supply {@code assigneeId} at creation have it silently ignored.
   */
  @Transactional
  public Task createTask(
      UUID projectId,
      String title,
      String description,
      String priority,
      String type,
      LocalDate dueDate,
      UUID createdBy,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID assigneeId) {
    return createTask(
        projectId,
        title,
        description,
        priority,
        type,
        dueDate,
        createdBy,
        orgRole,
        customFields,
        appliedFieldGroups,
        assigneeId,
        null,
        null);
  }

  @Transactional
  public Task createTask(
      UUID projectId,
      String title,
      String description,
      String priority,
      String type,
      LocalDate dueDate,
      UUID createdBy,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID assigneeId,
      String recurrenceRule,
      LocalDate recurrenceEndDate) {
    // Any project member can create tasks; view access is sufficient
    projectAccessService.requireViewAccess(projectId, createdBy, orgRole);

    // Check project is not archived
    projectLifecycleGuard.requireNotReadOnly(projectId);

    // Validate recurrence rule format if provided
    if (recurrenceRule != null && !recurrenceRule.isBlank()) {
      try {
        RecurrenceRule.parse(recurrenceRule);
      } catch (IllegalArgumentException e) {
        throw new InvalidStateException(
            "Invalid recurrence rule", "Invalid recurrence rule format: " + e.getMessage());
      }
    }

    // Validate custom fields
    Map<String, Object> validatedFields =
        customFieldValidator.validate(
            EntityType.TASK,
            customFields != null ? customFields : new HashMap<>(),
            appliedFieldGroups);

    var task = new Task(projectId, title, description, priority, type, dueDate, createdBy);
    task.setCustomFields(validatedFields);
    if (appliedFieldGroups != null) {
      task.setAppliedFieldGroups(appliedFieldGroups);
    }
    if (recurrenceRule != null) {
      task.setRecurrenceRule(recurrenceRule);
    }
    if (recurrenceEndDate != null) {
      task.setRecurrenceEndDate(recurrenceEndDate);
    }

    // Auto-apply field groups before save so audit events capture final state
    var autoApplyIds = fieldGroupService.resolveAutoApplyGroupIds(EntityType.TASK);
    if (!autoApplyIds.isEmpty()) {
      var merged =
          new ArrayList<>(
              task.getAppliedFieldGroups() != null ? task.getAppliedFieldGroups() : List.of());
      for (UUID id : autoApplyIds) {
        if (!merged.contains(id)) {
          merged.add(id);
        }
      }
      task.setAppliedFieldGroups(merged);
    }
    task = taskRepository.save(task);

    // Pre-assign at creation (admin/owner only; silently ignore for regular members)
    boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
    if (assigneeId != null && isAdminOrOwner) {
      if (!projectMemberRepository.existsByProjectIdAndMemberId(projectId, assigneeId)) {
        throw new ResourceNotFoundException("ProjectMember", assigneeId);
      }
      task.claim(assigneeId);
      // TODO: Send assignee notification on pre-assign (deferred — task.created event captures
      // assignee_id in audit details)
      task = taskRepository.save(task);
    }

    log.info("Created task {} in project {}", task.getId(), projectId);

    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("title", task.getTitle());
    auditDetails.put("project_id", projectId.toString());
    if (task.getAssigneeId() != null) {
      auditDetails.put("assignee_id", task.getAssigneeId().toString());
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.created")
            .entityType("task")
            .entityId(task.getId())
            .details(auditDetails)
            .build());

    // Publish portal task event if project is customer-linked
    publishPortalTaskEventIfLinked(task, PortalTaskCreatedEvent::new);

    return task;
  }

  @Transactional
  public Task updateTask(
      UUID taskId,
      String title,
      String description,
      String priority,
      String status,
      String type,
      LocalDate dueDate,
      UUID assigneeId,
      UUID memberId,
      String orgRole) {
    return updateTask(
        taskId,
        title,
        description,
        priority,
        status,
        type,
        dueDate,
        assigneeId,
        memberId,
        orgRole,
        null,
        null,
        null,
        null);
  }

  @Transactional
  public Task updateTask(
      UUID taskId,
      String title,
      String description,
      String priority,
      String status,
      String type,
      LocalDate dueDate,
      UUID assigneeId,
      UUID memberId,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {
    return updateTask(
        taskId,
        title,
        description,
        priority,
        status,
        type,
        dueDate,
        assigneeId,
        memberId,
        orgRole,
        customFields,
        appliedFieldGroups,
        null,
        null);
  }

  @Transactional
  public Task updateTask(
      UUID taskId,
      String title,
      String description,
      String priority,
      String status,
      String type,
      LocalDate dueDate,
      UUID assigneeId,
      UUID memberId,
      String orgRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      String recurrenceRule,
      LocalDate recurrenceEndDate) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Lead/admin/owner can update any task; contributors can only update their own assigned tasks
    if (!access.canEdit() && !memberId.equals(task.getAssigneeId())) {
      throw new ForbiddenException(
          "Cannot update task", "You do not have permission to update task " + taskId);
    }

    if (assigneeId != null
        && !projectMemberRepository.existsByProjectIdAndMemberId(task.getProjectId(), assigneeId)) {
      throw new ResourceNotFoundException("ProjectMember", assigneeId);
    }

    // Validate recurrence rule format if provided
    String effectiveRecurrenceRule =
        recurrenceRule != null ? recurrenceRule : task.getRecurrenceRule();
    LocalDate effectiveRecurrenceEndDate =
        recurrenceEndDate != null ? recurrenceEndDate : task.getRecurrenceEndDate();
    if (recurrenceRule != null && !recurrenceRule.isBlank()) {
      try {
        RecurrenceRule.parse(recurrenceRule);
      } catch (IllegalArgumentException e) {
        throw new InvalidStateException(
            "Invalid recurrence rule", "Invalid recurrence rule format: " + e.getMessage());
      }
    }

    // Validate and set custom fields
    if (customFields != null) {
      Map<String, Object> validatedFields =
          customFieldValidator.validate(
              EntityType.TASK,
              customFields,
              appliedFieldGroups != null ? appliedFieldGroups : task.getAppliedFieldGroups());
      task.setCustomFields(validatedFields);
    }
    if (appliedFieldGroups != null) {
      task.setAppliedFieldGroups(appliedFieldGroups);
    }

    // Convert string inputs to enums
    TaskPriority taskPriority = parsePriority(priority);
    TaskStatus taskStatus = parseStatus(status);

    // Capture old values before mutation
    String oldTitle = task.getTitle();
    String oldDescription = task.getDescription();
    TaskStatus oldStatus = task.getStatus();
    TaskPriority oldPriority = task.getPriority();
    UUID oldAssigneeId = task.getAssigneeId();
    LocalDate oldDueDate = task.getDueDate();
    String oldType = task.getType();

    task.update(
        title,
        description,
        taskPriority,
        taskStatus,
        type,
        dueDate,
        assigneeId,
        memberId,
        effectiveRecurrenceRule,
        effectiveRecurrenceEndDate);
    task = taskRepository.save(task);

    // Build delta map -- only include changed fields
    var details = new LinkedHashMap<String, Object>();
    if (!Objects.equals(oldTitle, title)) {
      details.put(
          "title",
          Map.of("from", oldTitle != null ? oldTitle : "", "to", title != null ? title : ""));
    }
    if (!Objects.equals(oldDescription, description)) {
      details.put(
          "description",
          Map.of(
              "from", oldDescription != null ? oldDescription : "",
              "to", description != null ? description : ""));
    }
    if (oldStatus != taskStatus) {
      details.put("status", Map.of("from", oldStatus.name(), "to", taskStatus.name()));
    }
    if (oldPriority != taskPriority) {
      details.put("priority", Map.of("from", oldPriority.name(), "to", taskPriority.name()));
    }
    if (!Objects.equals(oldAssigneeId, assigneeId)) {
      details.put(
          "assignee_id",
          Map.of(
              "from", oldAssigneeId != null ? oldAssigneeId.toString() : "",
              "to", assigneeId != null ? assigneeId.toString() : ""));
    }
    if (!Objects.equals(oldDueDate, dueDate)) {
      details.put(
          "due_date",
          Map.of(
              "from", oldDueDate != null ? oldDueDate.toString() : "",
              "to", dueDate != null ? dueDate.toString() : ""));
    }
    if (!Objects.equals(oldType, type)) {
      details.put(
          "type", Map.of("from", oldType != null ? oldType : "", "to", type != null ? type : ""));
    }

    details.put("project_id", task.getProjectId().toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.updated")
            .entityType("task")
            .entityId(task.getId())
            .details(details)
            .build());

    // Event publication for notification fan-out
    boolean assigneeChanged = !Objects.equals(oldAssigneeId, assigneeId);
    boolean statusChanged = oldStatus != taskStatus;
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();

    if (assigneeChanged && assigneeId != null) {
      String actorName = resolveActorName(memberId);
      eventPublisher.publishEvent(
          new TaskAssignedEvent(
              "task.assigned",
              "task",
              task.getId(),
              task.getProjectId(),
              memberId,
              actorName,
              tenantId,
              orgId,
              Instant.now(),
              Map.of("title", task.getTitle()),
              assigneeId,
              task.getTitle()));
    }
    if (statusChanged) {
      String actorName = resolveActorName(memberId);
      eventPublisher.publishEvent(
          new TaskStatusChangedEvent(
              "task.status_changed",
              "task",
              task.getId(),
              task.getProjectId(),
              memberId,
              actorName,
              tenantId,
              orgId,
              Instant.now(),
              Map.of(
                  "title",
                  task.getTitle(),
                  "old_status",
                  oldStatus.name(),
                  "new_status",
                  taskStatus.name()),
              oldStatus.name(),
              taskStatus.name(),
              task.getAssigneeId(),
              task.getTitle()));
    }

    // Publish portal task event if project is customer-linked
    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    return task;
  }

  @Transactional
  public List<FieldDefinitionResponse> setFieldGroups(
      UUID taskId, List<UUID> appliedFieldGroups, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    if (!access.canEdit() && !memberId.equals(task.getAssigneeId())) {
      throw new ForbiddenException(
          "Cannot update task", "You do not have permission to update task " + taskId);
    }

    // Validate all field groups exist and match entity type
    for (UUID groupId : appliedFieldGroups) {
      var group =
          fieldGroupRepository
              .findById(groupId)
              .orElseThrow(() -> new ResourceNotFoundException("FieldGroup", groupId));
      if (group.getEntityType() != EntityType.TASK) {
        throw new InvalidStateException(
            "Invalid field group", "Field group " + groupId + " is not for entity type TASK");
      }
    }

    // Resolve one-level dependencies
    appliedFieldGroups = fieldGroupService.resolveDependencies(appliedFieldGroups);

    task.setAppliedFieldGroups(appliedFieldGroups);
    taskRepository.save(task);

    // Collect field definition IDs from applied groups
    var fieldDefIds = new ArrayList<UUID>();
    for (UUID groupId : appliedFieldGroups) {
      var members = fieldGroupMemberRepository.findByFieldGroupIdOrderBySortOrder(groupId);
      for (var member : members) {
        fieldDefIds.add(member.getFieldDefinitionId());
      }
    }

    return fieldDefIds.stream()
        .distinct()
        .map(fdId -> fieldDefinitionRepository.findById(fdId))
        .filter(java.util.Optional::isPresent)
        .map(java.util.Optional::get)
        .map(FieldDefinitionResponse::from)
        .toList();
  }

  @Transactional
  public void deleteTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Only lead/admin/owner can delete tasks
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot delete task", "You do not have permission to delete task " + taskId);
    }

    // Guard: time entries exist
    long timeEntryCount = timeEntryRepository.countByTaskId(taskId);
    if (timeEntryCount > 0) {
      throw new ResourceConflictException(
          "Cannot delete task",
          "Task has %d time entry/entries. Cancel the task instead of deleting it."
              .formatted(timeEntryCount));
    }

    taskRepository.delete(task);
    log.info("Deleted task {} from project {}", taskId, task.getProjectId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.deleted")
            .entityType("task")
            .entityId(task.getId())
            .details(Map.of("title", task.getTitle(), "project_id", task.getProjectId().toString()))
            .build());

    // Publish portal task deleted event if project is customer-linked
    var customerLinksForDelete = customerProjectRepository.findByProjectId(task.getProjectId());
    if (!customerLinksForDelete.isEmpty()) {
      String tenantId = RequestScopes.requireTenantId();
      String orgId = RequestScopes.requireOrgId();
      eventPublisher.publishEvent(new PortalTaskDeletedEvent(task.getId(), orgId, tenantId));
    }
  }

  @Transactional
  public Task claimTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Verify claimant is a project member
    if (!projectMemberRepository.existsByProjectIdAndMemberId(task.getProjectId(), memberId)) {
      throw new ResourceNotFoundException("Task", taskId);
    }

    if (task.getAssigneeId() != null) {
      throw new InvalidStateException(
          "Cannot claim task", "Task is already assigned to another member");
    }

    task.claim(memberId);
    task = taskRepository.save(task);
    log.info("Task {} claimed by member {}", taskId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.claimed")
            .entityType("task")
            .entityId(task.getId())
            .details(
                Map.of(
                    "title", task.getTitle(),
                    "assignee_id", memberId.toString(),
                    "project_id", task.getProjectId().toString()))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new TaskClaimedEvent(
            "task.claimed",
            "task",
            task.getId(),
            task.getProjectId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("title", task.getTitle()),
            null, // previousAssigneeId (always null — claim only works on unassigned tasks)
            task.getTitle()));

    // Publish portal task updated event if project is customer-linked
    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    return task;
  }

  @Transactional
  public Task releaseTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    if (task.getAssigneeId() == null) {
      throw new InvalidStateException("Cannot release task", "Task is not currently claimed");
    }

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Current assignee or lead/admin/owner can release
    boolean isAssignee = memberId.equals(task.getAssigneeId());
    if (!isAssignee && !access.canEdit()) {
      throw new ForbiddenException(
          "Cannot release task", "Only the current assignee or a lead/admin/owner can release");
    }

    // Capture assignee before release clears it
    UUID previousAssigneeId = task.getAssigneeId();

    task.release();
    task = taskRepository.save(task);
    log.info("Task {} released by member {}", taskId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.released")
            .entityType("task")
            .entityId(task.getId())
            .details(
                Map.of(
                    "title", task.getTitle(),
                    "previous_assignee_id", previousAssigneeId.toString(),
                    "project_id", task.getProjectId().toString()))
            .build());

    // Publish portal task updated event if project is customer-linked
    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    return task;
  }

  /** Result of completing a task, optionally including the next recurring instance. */
  public record CompleteTaskResult(Task completedTask, Task nextInstance) {}

  @Transactional
  public CompleteTaskResult completeTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Only assignee or admin/owner can complete
    boolean isAssignee = memberId.equals(task.getAssigneeId());
    boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
    if (!isAssignee && !isAdminOrOwner) {
      throw new ForbiddenException(
          "Cannot complete task", "Only the assignee or an admin/owner can complete this task");
    }

    task.complete(memberId);
    task = taskRepository.save(task);
    log.info("Task {} completed by member {}", taskId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.completed")
            .entityType("task")
            .entityId(task.getId())
            .details(
                Map.of(
                    "title", task.getTitle(),
                    "project_id", task.getProjectId().toString(),
                    "completed_by", memberId.toString()))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new TaskCompletedEvent(
            "task.completed",
            "task",
            task.getId(),
            task.getProjectId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("title", task.getTitle()),
            memberId,
            task.getTitle()));

    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    // --- Recurrence auto-creation ---
    Task nextInstance = null;
    if (task.isRecurring()) {
      nextInstance = createRecurringNextInstance(task, memberId, actorName, tenantId, orgId);
    }

    return new CompleteTaskResult(task, nextInstance);
  }

  /**
   * Creates the next recurring task instance when a recurring task is completed. Runs in the same
   * transaction. Returns null if recurrence has expired.
   */
  private Task createRecurringNextInstance(
      Task completedTask, UUID memberId, String actorName, String tenantId, String orgId) {
    var rule = RecurrenceRule.parse(completedTask.getRecurrenceRule());
    LocalDate nextDueDate = rule.calculateNextDueDate(completedTask.getDueDate());

    // Check if recurrence has expired
    if (completedTask.getRecurrenceEndDate() != null
        && nextDueDate.isAfter(completedTask.getRecurrenceEndDate())) {
      log.info(
          "Recurrence expired for task {} — end date {} passed",
          completedTask.getId(),
          completedTask.getRecurrenceEndDate());
      return null;
    }

    // Also check if end date itself has already passed (before calculating next)
    if (completedTask.getRecurrenceEndDate() != null
        && LocalDate.now().isAfter(completedTask.getRecurrenceEndDate())) {
      log.info(
          "Recurrence end date {} already passed for task {}",
          completedTask.getRecurrenceEndDate(),
          completedTask.getId());
      return null;
    }

    // Create next instance
    var nextTask =
        new Task(
            completedTask.getProjectId(),
            completedTask.getTitle(),
            completedTask.getDescription(),
            completedTask.getPriority().name(),
            completedTask.getType(),
            nextDueDate,
            memberId);
    nextTask.setRecurrenceRule(completedTask.getRecurrenceRule());
    nextTask.setRecurrenceEndDate(completedTask.getRecurrenceEndDate());
    nextTask.setParentTaskId(completedTask.getRootTaskId());

    // Copy assignee if present
    if (completedTask.getAssigneeId() != null) {
      nextTask.claim(completedTask.getAssigneeId());
    }

    nextTask = taskRepository.save(nextTask);
    log.info(
        "Created recurring task instance {} from completed task {}, due {}",
        nextTask.getId(),
        completedTask.getId(),
        nextDueDate);

    // Audit event for the new instance
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.recurrence_created")
            .entityType("task")
            .entityId(nextTask.getId())
            .details(
                Map.of(
                    "title", nextTask.getTitle(),
                    "project_id", nextTask.getProjectId().toString(),
                    "parent_task_id", nextTask.getParentTaskId().toString(),
                    "due_date", nextDueDate.toString(),
                    "source_task_id", completedTask.getId().toString()))
            .build());

    // Publish event for notification fan-out
    eventPublisher.publishEvent(
        new TaskRecurrenceCreatedEvent(
            "task.recurrence_created",
            "task",
            nextTask.getId(),
            nextTask.getProjectId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("title", nextTask.getTitle(), "due_date", nextDueDate.toString()),
            nextTask.getAssigneeId(),
            nextTask.getTitle(),
            nextDueDate,
            nextTask.getParentTaskId()));

    // Publish portal event if project is customer-linked
    publishPortalTaskEventIfLinked(nextTask, PortalTaskCreatedEvent::new);

    return nextTask;
  }

  @Transactional
  public Task cancelTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Only admin/owner can cancel
    boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
    if (!isAdminOrOwner) {
      throw new ForbiddenException("Cannot cancel task", "Only an admin or owner can cancel tasks");
    }

    task.cancel(memberId);
    task = taskRepository.save(task);
    log.info("Task {} cancelled by member {}", taskId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.cancelled")
            .entityType("task")
            .entityId(task.getId())
            .details(
                Map.of(
                    "title", task.getTitle(),
                    "project_id", task.getProjectId().toString(),
                    "cancelled_by", memberId.toString()))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new TaskCancelledEvent(
            "task.cancelled",
            "task",
            task.getId(),
            task.getProjectId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("title", task.getTitle()),
            memberId,
            task.getAssigneeId(),
            task.getTitle()));

    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    // Recurrence note: no new instance is created on cancel. Recurrence stops silently.
    // The user can reopen the task and complete it normally to resume recurrence.

    return task;
  }

  @Transactional
  public Task reopenTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    // Admin/owner or original assignee can reopen
    boolean isAdminOrOwner = "admin".equals(orgRole) || "owner".equals(orgRole);
    boolean isAssignee = memberId.equals(task.getAssigneeId());
    if (!isAdminOrOwner && !isAssignee) {
      throw new ForbiddenException(
          "Cannot reopen task", "Only an admin/owner or the assignee can reopen this task");
    }

    String previousStatus = task.getStatus().name();

    task.reopen();
    task = taskRepository.save(task);
    log.info("Task {} reopened by member {}", taskId, memberId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.reopened")
            .entityType("task")
            .entityId(task.getId())
            .details(
                Map.of(
                    "title", task.getTitle(),
                    "project_id", task.getProjectId().toString(),
                    "reopened_by", memberId.toString(),
                    "previous_status", previousStatus))
            .build());

    String actorName = resolveActorName(memberId);
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    eventPublisher.publishEvent(
        new TaskReopenedEvent(
            "task.reopened",
            "task",
            task.getId(),
            task.getProjectId(),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("title", task.getTitle()),
            memberId,
            task.getTitle(),
            previousStatus));

    publishPortalTaskEventIfLinked(task, PortalTaskUpdatedEvent::new);

    return task;
  }

  /**
   * Publishes a portal task event if the task's project is linked to any customer. The event
   * factory is used to construct the correct event subtype (created, updated, deleted).
   */
  private void publishPortalTaskEventIfLinked(Task task, PortalTaskEventFactory eventFactory) {
    var customerLinks = customerProjectRepository.findByProjectId(task.getProjectId());
    if (!customerLinks.isEmpty()) {
      String tenantId = RequestScopes.requireTenantId();
      String orgId = RequestScopes.requireOrgId();
      String assigneeName =
          task.getAssigneeId() != null
              ? memberNameResolver.resolveNameOrNull(task.getAssigneeId())
              : null;
      eventPublisher.publishEvent(
          eventFactory.create(
              task.getId(),
              task.getProjectId(),
              task.getTitle(),
              task.getStatus().name(),
              assigneeName,
              0,
              orgId,
              tenantId));
    }
  }

  @FunctionalInterface
  private interface PortalTaskEventFactory {
    Object create(
        UUID taskId,
        UUID projectId,
        String name,
        String status,
        String assigneeName,
        int sortOrder,
        String orgId,
        String tenantId);
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
  }

  private static List<TaskStatus> parseStatuses(String statuses) {
    return java.util.Arrays.stream(statuses.split(","))
        .map(String::trim)
        .map(TaskService::parseStatus)
        .toList();
  }

  private static TaskStatus parseStatus(String status) {
    try {
      return TaskStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid task status", "Invalid task status: '" + status + "'");
    }
  }

  private static TaskPriority parsePriority(String priority) {
    try {
      return TaskPriority.valueOf(priority);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid task priority", "Invalid task priority: '" + priority + "'");
    }
  }
}
