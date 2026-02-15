package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

  public TaskController(
      TaskService taskService,
      MemberRepository memberRepository,
      EntityTagService entityTagService) {
    this.taskService = taskService;
    this.memberRepository = memberRepository;
    this.entityTagService = entityTagService;
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
            request.appliedFieldGroups());

    var names = resolveNames(List.of(task));
    return ResponseEntity.created(URI.create("/api/tasks/" + task.getId()))
        .body(TaskResponse.from(task, names, List.of()));
  }

  @GetMapping("/api/projects/{projectId}/tasks")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaskResponse>> listTasks(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String assigneeFilter,
      @RequestParam(required = false) Map<String, String> allParams) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var taskEntities =
        taskService.listTasks(
            projectId, memberId, orgRole, status, assigneeId, priority, assigneeFilter);
    var names = resolveNames(taskEntities);
    var tasks =
        taskEntities.stream()
            .map(
                t -> TaskResponse.from(t, names, entityTagService.getEntityTags("TASK", t.getId())))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters = extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      tasks =
          tasks.stream()
              .filter(t -> matchesCustomFieldFilters(t.customFields(), customFieldFilters))
              .toList();
    }

    // Apply tag filtering if present
    List<String> tagSlugs = extractTagSlugs(allParams);
    if (!tagSlugs.isEmpty()) {
      tasks = tasks.stream().filter(t -> matchesTagFilter(t.tags(), tagSlugs)).toList();
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
      @PathVariable UUID id, @RequestBody SetEntityTagsRequest request) {
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
            .flatMap(t -> Stream.of(t.getAssigneeId(), t.getCreatedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
  }

  private Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  private boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    if (customFields == null) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      Object fieldValue = customFields.get(entry.getKey());
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private List<String> extractTagSlugs(Map<String, String> allParams) {
    if (allParams == null || !allParams.containsKey("tags")) {
      return List.of();
    }
    String tagsParam = allParams.get("tags");
    if (tagsParam == null || tagsParam.isBlank()) {
      return List.of();
    }
    return Arrays.stream(tagsParam.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private boolean matchesTagFilter(List<TagResponse> entityTags, List<String> requiredSlugs) {
    if (entityTags == null) {
      return false;
    }
    Set<String> tagSlugs = entityTags.stream().map(TagResponse::slug).collect(Collectors.toSet());
    return tagSlugs.containsAll(requiredSlugs);
  }

  // --- DTOs ---

  public record CreateTaskRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      String description,
      @Size(max = 20, message = "priority must be at most 20 characters") String priority,
      @Size(max = 100, message = "type must be at most 100 characters") String type,
      LocalDate dueDate,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

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
          task.getStatus(),
          task.getPriority(),
          task.getType(),
          task.getAssigneeId(),
          task.getAssigneeId() != null ? memberNames.get(task.getAssigneeId()) : null,
          task.getCreatedBy(),
          memberNames.get(task.getCreatedBy()),
          task.getDueDate(),
          task.getVersion(),
          task.getCreatedAt(),
          task.getUpdatedAt(),
          task.getCustomFields(),
          task.getAppliedFieldGroups(),
          tags);
    }
  }
}
