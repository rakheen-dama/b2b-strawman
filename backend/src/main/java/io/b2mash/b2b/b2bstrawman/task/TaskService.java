package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.event.TaskAssignedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskClaimedEvent;
import io.b2mash.b2b.b2bstrawman.event.TaskStatusChangedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.CustomFieldValidator;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
  private final MemberRepository memberRepository;
  private final CustomFieldValidator customFieldValidator;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;

  public TaskService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      ProjectMemberRepository projectMemberRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberRepository memberRepository,
      CustomFieldValidator customFieldValidator,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      FieldDefinitionRepository fieldDefinitionRepository) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.projectMemberRepository = projectMemberRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberRepository = memberRepository;
    this.customFieldValidator = customFieldValidator;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
  }

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

    // Handle special "unassigned" filter
    if ("unassigned".equals(assigneeFilter)) {
      return taskRepository.findByProjectIdUnassigned(projectId, status, priority);
    }

    return taskRepository.findByProjectIdWithFilters(projectId, status, assigneeId, priority);
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
        projectId, title, description, priority, type, dueDate, createdBy, orgRole, null, null);
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
      List<UUID> appliedFieldGroups) {
    // Any project member can create tasks; view access is sufficient
    projectAccessService.requireViewAccess(projectId, createdBy, orgRole);

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
    task = taskRepository.save(task);
    log.info("Created task {} in project {}", task.getId(), projectId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.created")
            .entityType("task")
            .entityId(task.getId())
            .details(Map.of("title", task.getTitle(), "project_id", projectId.toString()))
            .build());

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

    // Capture old values before mutation
    String oldTitle = task.getTitle();
    String oldDescription = task.getDescription();
    String oldStatus = task.getStatus();
    String oldPriority = task.getPriority();
    UUID oldAssigneeId = task.getAssigneeId();
    LocalDate oldDueDate = task.getDueDate();
    String oldType = task.getType();

    task.update(title, description, priority, status, type, dueDate, assigneeId);
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
    if (!Objects.equals(oldStatus, status)) {
      details.put(
          "status",
          Map.of("from", oldStatus != null ? oldStatus : "", "to", status != null ? status : ""));
    }
    if (!Objects.equals(oldPriority, priority)) {
      details.put(
          "priority",
          Map.of(
              "from", oldPriority != null ? oldPriority : "",
              "to", priority != null ? priority : ""));
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
    boolean statusChanged = !Objects.equals(oldStatus, status);
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;

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
    if (statusChanged && status != null) {
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
                  oldStatus != null ? oldStatus : "",
                  "new_status",
                  status),
              oldStatus != null ? oldStatus : "",
              status,
              task.getAssigneeId(),
              task.getTitle()));
    }

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

    taskRepository.delete(task);
    log.info("Deleted task {} from project {}", taskId, task.getProjectId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.deleted")
            .entityType("task")
            .entityId(task.getId())
            .details(Map.of("title", task.getTitle(), "project_id", task.getProjectId().toString()))
            .build());
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

    if (!"OPEN".equals(task.getStatus())) {
      throw new InvalidStateException(
          "Cannot claim task",
          "Task can only be claimed when status is OPEN. Current status: " + task.getStatus());
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
    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
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

    return task;
  }

  private String resolveActorName(UUID memberId) {
    return memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
  }
}
