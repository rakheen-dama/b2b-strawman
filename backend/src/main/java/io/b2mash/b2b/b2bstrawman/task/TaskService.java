package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private static final Logger log = LoggerFactory.getLogger(TaskService.class);

  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final ProjectMemberRepository projectMemberRepository;
  private final AuditService auditService;

  public TaskService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      ProjectMemberRepository projectMemberRepository,
      AuditService auditService) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.projectMemberRepository = projectMemberRepository;
    this.auditService = auditService;
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
            .findOneById(taskId)
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
    // Any project member can create tasks; view access is sufficient
    projectAccessService.requireViewAccess(projectId, createdBy, orgRole);

    var task = new Task(projectId, title, description, priority, type, dueDate, createdBy);
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
    var task =
        taskRepository
            .findOneById(taskId)
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

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task.updated")
            .entityType("task")
            .entityId(task.getId())
            .details(details.isEmpty() ? null : details)
            .build());

    return task;
  }

  @Transactional
  public void deleteTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findOneById(taskId)
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
            .findOneById(taskId)
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
            .details(Map.of("assignee_id", memberId.toString()))
            .build());

    return task;
  }

  @Transactional
  public Task releaseTask(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findOneById(taskId)
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
            .details(Map.of("previous_assignee_id", previousAssigneeId.toString()))
            .build());

    return task;
  }
}
