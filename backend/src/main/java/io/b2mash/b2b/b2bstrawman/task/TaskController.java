package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

  private final TaskService taskService;
  private final MemberRepository memberRepository;
  private final EntityTagService entityTagService;
  private final ViewFilterHelper viewFilterHelper;

  public TaskController(
      TaskService taskService,
      MemberRepository memberRepository,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper) {
    this.taskService = taskService;
    this.memberRepository = memberRepository;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
  }

  @PostMapping("/api/projects/{projectId}/tasks")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> createTask(
      @PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task =
        taskService.createTask(
            projectId,
            request.title(),
            request.description(),
            request.priority(),
            request.type(),
            request.dueDate(),
            createdBy,
            orgRole,
            request.customFields(),
            request.appliedFieldGroups(),
            request.assigneeId());

    var names = resolveNames(List.of(task));
    return ResponseEntity.created(URI.create("/api/tasks/" + task.getId()))
        .body(TaskResponse.from(task, names, List.of()));
  }

  @GetMapping("/api/projects/{projectId}/tasks")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaskResponse>> listTasks(
      @PathVariable UUID projectId,
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String assigneeFilter,
      @RequestParam(required = false) Map<String, String> allParams) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      List<Task> filtered =
          viewFilterHelper.applyViewFilterForProject(view, "TASK", "tasks", Task.class, projectId);

      if (filtered != null) {
        var names = resolveNames(filtered);
        var taskIds = filtered.stream().map(Task::getId).toList();
        var tagsByEntityId = entityTagService.getEntityTagsBatch("TASK", taskIds);

        var responses =
            filtered.stream()
                .map(
                    t ->
                        TaskResponse.from(
                            t, names, tagsByEntityId.getOrDefault(t.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(responses);
      }
    }

    // --- Fallback: existing in-memory filtering ---
    var taskEntities =
        taskService.listTasks(
            projectId, memberId, orgRole, status, assigneeId, priority, assigneeFilter);
    var names = resolveNames(taskEntities);

    // Batch-load tags for all tasks (2 queries instead of 2N)
    var taskIds = taskEntities.stream().map(Task::getId).toList();
    var tagsByEntityId = entityTagService.getEntityTagsBatch("TASK", taskIds);

    var tasks =
        taskEntities.stream()
            .map(
                t -> TaskResponse.from(t, names, tagsByEntityId.getOrDefault(t.getId(), List.of())))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters =
        CustomFieldFilterUtil.extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      tasks =
          tasks.stream()
              .filter(
                  t ->
                      CustomFieldFilterUtil.matchesCustomFieldFilters(
                          t.customFields(), customFieldFilters))
              .toList();
    }

    // Apply tag filtering if present
    List<String> tagSlugs = TagFilterUtil.extractTagSlugs(allParams);
    if (!tagSlugs.isEmpty()) {
      tasks =
          tasks.stream().filter(t -> TagFilterUtil.matchesTagFilter(t.tags(), tagSlugs)).toList();
    }

    return ResponseEntity.ok(tasks);
  }

  @GetMapping("/api/tasks/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.getTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PutMapping("/api/tasks/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> updateTask(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task =
        taskService.updateTask(
            id,
            request.title(),
            request.description(),
            request.priority(),
            request.status(),
            request.type(),
            request.dueDate(),
            request.assigneeId(),
            memberId,
            orgRole,
            request.customFields(),
            request.appliedFieldGroups());

    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @DeleteMapping("/api/tasks/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    taskService.deleteTask(id, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/tasks/{id}/claim")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> claimTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.claimTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PostMapping("/api/tasks/{id}/release")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> releaseTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.releaseTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PatchMapping("/api/tasks/{id}/complete")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.completeTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PatchMapping("/api/tasks/{id}/cancel")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> cancelTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.cancelTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PatchMapping("/api/tasks/{id}/reopen")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskResponse> reopenTask(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var task = taskService.reopenTask(id, memberId, orgRole);
    var names = resolveNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PutMapping("/api/tasks/{id}/field-groups")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var fieldDefs = taskService.setFieldGroups(id, request.appliedFieldGroups(), memberId, orgRole);
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/api/tasks/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> setTaskTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    // Verify task access
    taskService.getTask(id, memberId, orgRole);
    var tags = entityTagService.setEntityTags("TASK", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/api/tasks/{id}/tags")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TagResponse>> getTaskTags(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    // Verify task access
    taskService.getTask(id, memberId, orgRole);
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(tags);
  }

  /**
   * Batch-loads member names for all assignee and createdBy IDs referenced by the given tasks.
   * Returns a map of member UUID to display name.
   */
  private Map<UUID, String> resolveNames(List<Task> tasks) {
    var ids =
        tasks.stream()
            .flatMap(t -> Stream.of(t.getAssigneeId(), t.getCreatedBy(), t.getCompletedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }

  // --- DTOs ---

  /**
   * Request body for task creation.
   *
   * @param assigneeId Optional. The member to pre-assign the task to at creation time. Only honored
   *     for admin/owner callers; silently ignored for regular members. See {@link
   *     TaskService#createTask} for the permission asymmetry rationale.
   */
  public record CreateTaskRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      String description,
      @Size(max = 20, message = "priority must be at most 20 characters") String priority,
      @Size(max = 100, message = "type must be at most 100 characters") String type,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      UUID assigneeId) {}

  public record UpdateTaskRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      String description,
      @NotBlank(message = "priority is required")
          @Size(max = 20, message = "priority must be at most 20 characters")
          String priority,
      @NotBlank(message = "status is required")
          @Size(max = 20, message = "status must be at most 20 characters")
          String status,
      @Size(max = 100, message = "type must be at most 100 characters") String type,
      LocalDate dueDate,
      UUID assigneeId,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record TaskResponse(
      UUID id,
      UUID projectId,
      String title,
      String description,
      String status,
      String priority,
      String type,
      UUID assigneeId,
      String assigneeName,
      UUID createdBy,
      String createdByName,
      LocalDate dueDate,
      int version,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt,
      UUID completedBy,
      String completedByName,
      Instant cancelledAt,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups,
      List<TagResponse> tags) {

    public static TaskResponse from(
        Task task, Map<UUID, String> memberNames, List<TagResponse> tags) {
      return new TaskResponse(
          task.getId(),
          task.getProjectId(),
          task.getTitle(),
          task.getDescription(),
          task.getStatus().name(),
          task.getPriority().name(),
          task.getType(),
          task.getAssigneeId(),
          task.getAssigneeId() != null ? memberNames.get(task.getAssigneeId()) : null,
          task.getCreatedBy(),
          memberNames.get(task.getCreatedBy()),
          task.getDueDate(),
          task.getVersion(),
          task.getCreatedAt(),
          task.getUpdatedAt(),
          task.getCompletedAt(),
          task.getCompletedBy(),
          task.getCompletedBy() != null ? memberNames.get(task.getCompletedBy()) : null,
          task.getCancelledAt(),
          task.getCustomFields(),
          task.getAppliedFieldGroups(),
          tags);
    }
  }
}
