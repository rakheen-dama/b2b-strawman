package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.tag.TagFilterUtil;
import io.b2mash.b2b.b2bstrawman.tag.dto.SetEntityTagsRequest;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import io.b2mash.b2b.b2bstrawman.task.dto.TaskDtos.CompleteTaskResponse;
import io.b2mash.b2b.b2bstrawman.task.dto.TaskDtos.CreateTaskRequest;
import io.b2mash.b2b.b2bstrawman.task.dto.TaskDtos.TaskResponse;
import io.b2mash.b2b.b2bstrawman.task.dto.TaskDtos.UpdateTaskRequest;
import io.b2mash.b2b.b2bstrawman.view.CustomFieldFilterUtil;
import io.b2mash.b2b.b2bstrawman.view.ViewFilterHelper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
  private final EntityTagService entityTagService;
  private final ViewFilterHelper viewFilterHelper;

  public TaskController(
      TaskService taskService,
      EntityTagService entityTagService,
      ViewFilterHelper viewFilterHelper) {
    this.taskService = taskService;
    this.entityTagService = entityTagService;
    this.viewFilterHelper = viewFilterHelper;
  }

  @PostMapping("/api/projects/{projectId}/tasks")
  public ResponseEntity<TaskResponse> createTask(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateTaskRequest request,
      ActorContext actor) {

    var task =
        taskService.createTask(
            projectId,
            request.title(),
            request.description(),
            request.priority(),
            request.type(),
            request.dueDate(),
            actor,
            request.customFields(),
            request.appliedFieldGroups(),
            request.assigneeId(),
            request.recurrenceRule(),
            request.recurrenceEndDate());

    var names = taskService.resolveTaskMemberNames(List.of(task));
    return ResponseEntity.created(URI.create("/api/tasks/" + task.getId()))
        .body(TaskResponse.from(task, names, List.of()));
  }

  @GetMapping("/api/projects/{projectId}/tasks")
  public ResponseEntity<List<TaskResponse>> listTasks(
      @PathVariable UUID projectId,
      @RequestParam(required = false) UUID view,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String assigneeFilter,
      @RequestParam(required = false) Boolean recurring,
      @RequestParam(required = false) Map<String, String> allParams,
      ActorContext actor) {

    // --- View-based filtering (server-side SQL) ---
    if (view != null) {
      List<Task> filtered =
          viewFilterHelper.applyViewFilterForProject(view, "TASK", "tasks", Task.class, projectId);

      if (filtered != null) {
        // Post-filter for recurring tasks when view-based path is used
        if (Boolean.TRUE.equals(recurring)) {
          filtered = filtered.stream().filter(Task::isRecurring).toList();
        }
        var names = taskService.resolveTaskMemberNames(filtered);
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
            projectId, actor, status, assigneeId, priority, assigneeFilter, recurring);
    var names = taskService.resolveTaskMemberNames(taskEntities);

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
  public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id, ActorContext actor) {

    var task = taskService.getTask(id, actor);
    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PutMapping("/api/tasks/{id}")
  public ResponseEntity<TaskResponse> updateTask(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request, ActorContext actor) {

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
            actor,
            request.customFields(),
            request.appliedFieldGroups(),
            request.recurrenceRule(),
            request.recurrenceEndDate());

    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @DeleteMapping("/api/tasks/{id}")
  public ResponseEntity<Void> deleteTask(@PathVariable UUID id, ActorContext actor) {

    taskService.deleteTask(id, actor);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/tasks/{id}/claim")
  public ResponseEntity<TaskResponse> claimTask(@PathVariable UUID id, ActorContext actor) {

    var task = taskService.claimTask(id, actor);
    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PostMapping("/api/tasks/{id}/release")
  public ResponseEntity<TaskResponse> releaseTask(@PathVariable UUID id, ActorContext actor) {

    var task = taskService.releaseTask(id, actor);
    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PatchMapping("/api/tasks/{id}/complete")
  public ResponseEntity<CompleteTaskResponse> completeTask(
      @PathVariable UUID id, ActorContext actor) {

    var result = taskService.completeTask(id, actor);
    return ResponseEntity.ok(buildCompleteTaskResponse(result, id));
  }

  @PatchMapping("/api/tasks/{id}/cancel")
  public ResponseEntity<TaskResponse> cancelTask(@PathVariable UUID id, ActorContext actor) {

    var task = taskService.cancelTask(id, actor);
    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PatchMapping("/api/tasks/{id}/reopen")
  public ResponseEntity<TaskResponse> reopenTask(@PathVariable UUID id, ActorContext actor) {

    var task = taskService.reopenTask(id, actor);
    var names = taskService.resolveTaskMemberNames(List.of(task));
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(TaskResponse.from(task, names, tags));
  }

  @PutMapping("/api/tasks/{id}/field-groups")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id,
      @Valid @RequestBody SetFieldGroupsRequest request,
      ActorContext actor) {
    var fieldDefs = taskService.setFieldGroups(id, request.appliedFieldGroups(), actor);
    return ResponseEntity.ok(fieldDefs);
  }

  @PostMapping("/api/tasks/{id}/tags")
  public ResponseEntity<List<TagResponse>> setTaskTags(
      @PathVariable UUID id, @Valid @RequestBody SetEntityTagsRequest request, ActorContext actor) {
    // Verify task access
    taskService.getTask(id, actor);
    var tags = entityTagService.setEntityTags("TASK", id, request.tagIds());
    return ResponseEntity.ok(tags);
  }

  @GetMapping("/api/tasks/{id}/tags")
  public ResponseEntity<List<TagResponse>> getTaskTags(@PathVariable UUID id, ActorContext actor) {
    // Verify task access
    taskService.getTask(id, actor);
    var tags = entityTagService.getEntityTags("TASK", id);
    return ResponseEntity.ok(tags);
  }

  /**
   * Assembles a backward-compatible CompleteTaskResponse from a CompleteTaskResult. The completed
   * task fields are unwrapped to the top level; nextInstance is an optional nested field.
   */
  private CompleteTaskResponse buildCompleteTaskResponse(
      TaskService.CompleteTaskResult result, UUID completedTaskId) {
    var tasksForNames =
        result.nextInstance() != null
            ? List.of(result.completedTask(), result.nextInstance())
            : List.of(result.completedTask());
    var names = taskService.resolveTaskMemberNames(tasksForNames);
    var tags = entityTagService.getEntityTags("TASK", completedTaskId);

    TaskResponse nextInstanceResponse = null;
    if (result.nextInstance() != null) {
      var nextTags = entityTagService.getEntityTags("TASK", result.nextInstance().getId());
      nextInstanceResponse = TaskResponse.from(result.nextInstance(), names, nextTags);
    }

    return new CompleteTaskResponse(
        TaskResponse.from(result.completedTask(), names, tags), nextInstanceResponse);
  }
}
