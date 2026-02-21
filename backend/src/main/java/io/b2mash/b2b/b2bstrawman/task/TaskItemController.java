package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskItemController {

  private final TaskItemService taskItemService;

  public TaskItemController(TaskItemService taskItemService) {
    this.taskItemService = taskItemService;
  }

  @GetMapping("/api/tasks/{taskId}/items")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaskItemResponse>> listItems(@PathVariable UUID taskId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var items = taskItemService.listItems(taskId, memberId, orgRole);
    var response = items.stream().map(TaskItemResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/api/tasks/{taskId}/items")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskItemResponse> addItem(
      @PathVariable UUID taskId, @Valid @RequestBody CreateTaskItemRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
    var item = taskItemService.addItem(taskId, request.title(), sortOrder, memberId, orgRole);
    return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/items/" + item.getId()))
        .body(TaskItemResponse.from(item));
  }

  @PutMapping("/api/tasks/{taskId}/items/{itemId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskItemResponse> updateItem(
      @PathVariable UUID taskId,
      @PathVariable UUID itemId,
      @Valid @RequestBody UpdateTaskItemRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var item =
        taskItemService.updateItem(itemId, request.title(), request.sortOrder(), memberId, orgRole);
    return ResponseEntity.ok(TaskItemResponse.from(item));
  }

  @PutMapping("/api/tasks/{taskId}/items/{itemId}/toggle")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskItemResponse> toggleItem(
      @PathVariable UUID taskId, @PathVariable UUID itemId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var item = taskItemService.toggleItem(itemId, memberId, orgRole);
    return ResponseEntity.ok(TaskItemResponse.from(item));
  }

  @DeleteMapping("/api/tasks/{taskId}/items/{itemId}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteItem(@PathVariable UUID taskId, @PathVariable UUID itemId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    taskItemService.deleteItem(itemId, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/api/tasks/{taskId}/items/reorder")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TaskItemResponse>> reorderItems(
      @PathVariable UUID taskId, @Valid @RequestBody ReorderRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var items = taskItemService.reorderItems(taskId, request.orderedIds(), memberId, orgRole);
    var response = items.stream().map(TaskItemResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record CreateTaskItemRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      Integer sortOrder) {}

  public record UpdateTaskItemRequest(
      @NotBlank(message = "title is required")
          @Size(max = 500, message = "title must be at most 500 characters")
          String title,
      int sortOrder) {}

  public record ReorderRequest(List<UUID> orderedIds) {}

  public record TaskItemResponse(
      UUID id,
      UUID taskId,
      String title,
      boolean completed,
      int sortOrder,
      Instant createdAt,
      Instant updatedAt) {

    public static TaskItemResponse from(TaskItem item) {
      return new TaskItemResponse(
          item.getId(),
          item.getTaskId(),
          item.getTitle(),
          item.isCompleted(),
          item.getSortOrder(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }
}
