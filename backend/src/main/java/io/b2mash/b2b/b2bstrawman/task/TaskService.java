package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import java.time.LocalDate;
import java.util.List;
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

  public TaskService(
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      ProjectMemberRepository projectMemberRepository) {
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.projectMemberRepository = projectMemberRepository;
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

    task.update(title, description, priority, status, type, dueDate, assigneeId);
    return taskRepository.save(task);
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

    task.release();
    task = taskRepository.save(task);
    log.info("Task {} released by member {}", taskId, memberId);
    return task;
  }
}
