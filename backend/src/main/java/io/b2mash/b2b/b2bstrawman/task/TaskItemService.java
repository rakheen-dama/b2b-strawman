package io.b2mash.b2b.b2bstrawman.task;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskItemService {

  private static final Logger log = LoggerFactory.getLogger(TaskItemService.class);

  private final TaskItemRepository taskItemRepository;
  private final TaskRepository taskRepository;
  private final ProjectAccessService projectAccessService;
  private final AuditService auditService;

  public TaskItemService(
      TaskItemRepository taskItemRepository,
      TaskRepository taskRepository,
      ProjectAccessService projectAccessService,
      AuditService auditService) {
    this.taskItemRepository = taskItemRepository;
    this.taskRepository = taskRepository;
    this.projectAccessService = projectAccessService;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<TaskItem> listItems(UUID taskId, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);
    return taskItemRepository.findByTaskIdOrderBySortOrder(taskId);
  }

  @Transactional
  public TaskItem addItem(UUID taskId, String title, int sortOrder, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    var item = new TaskItem(taskId, title, sortOrder);
    item = taskItemRepository.save(item);

    log.info("Added task item {} to task {}", item.getId(), taskId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task_item.created")
            .entityType("task_item")
            .entityId(item.getId())
            .details(Map.of("title", title, "task_id", taskId.toString()))
            .build());

    return item;
  }

  @Transactional
  public TaskItem updateItem(
      UUID itemId, String title, int sortOrder, UUID memberId, String orgRole) {
    var foundItem =
        taskItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("TaskItem", itemId));
    UUID parentTaskId = foundItem.getTaskId();
    var task =
        taskRepository
            .findById(parentTaskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", parentTaskId));

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot update task item", "You do not have permission to update task item " + itemId);
    }

    String oldTitle = foundItem.getTitle();
    int oldSortOrder = foundItem.getSortOrder();
    foundItem.update(title, sortOrder);
    var item = taskItemRepository.save(foundItem);

    log.info("Updated task item {}", itemId);

    var details = new LinkedHashMap<String, Object>();
    details.put("task_id", item.getTaskId().toString());
    if (!oldTitle.equals(title)) {
      details.put("title", Map.of("from", oldTitle, "to", title));
    }
    if (oldSortOrder != sortOrder) {
      details.put("sort_order", Map.of("from", oldSortOrder, "to", sortOrder));
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task_item.updated")
            .entityType("task_item")
            .entityId(item.getId())
            .details(details)
            .build());

    return item;
  }

  @Transactional
  public TaskItem toggleItem(UUID itemId, UUID memberId, String orgRole) {
    var foundItem =
        taskItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("TaskItem", itemId));
    UUID parentTaskId = foundItem.getTaskId();
    var task =
        taskRepository
            .findById(parentTaskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", parentTaskId));

    // Any project member can toggle
    projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);

    boolean wasBefore = foundItem.isCompleted();
    foundItem.toggle();
    var item = taskItemRepository.save(foundItem);

    log.info("Toggled task item {} to completed={}", itemId, item.isCompleted());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task_item.toggled")
            .entityType("task_item")
            .entityId(item.getId())
            .details(
                Map.of(
                    "task_id", item.getTaskId().toString(),
                    "completed", Map.of("from", wasBefore, "to", item.isCompleted())))
            .build());

    return item;
  }

  @Transactional
  public void deleteItem(UUID itemId, UUID memberId, String orgRole) {
    var item =
        taskItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("TaskItem", itemId));
    var task =
        taskRepository
            .findById(item.getTaskId())
            .orElseThrow(() -> new ResourceNotFoundException("Task", item.getTaskId()));

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot delete task item", "You do not have permission to delete task item " + itemId);
    }

    taskItemRepository.delete(item);
    log.info("Deleted task item {} from task {}", itemId, item.getTaskId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task_item.deleted")
            .entityType("task_item")
            .entityId(item.getId())
            .details(
                Map.of(
                    "title", item.getTitle(),
                    "task_id", item.getTaskId().toString()))
            .build());
  }

  @Transactional
  public List<TaskItem> reorderItems(
      UUID taskId, List<UUID> orderedIds, UUID memberId, String orgRole) {
    var task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

    var access = projectAccessService.requireViewAccess(task.getProjectId(), memberId, orgRole);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot reorder task items",
          "You do not have permission to reorder items on task " + taskId);
    }

    var existingItems = taskItemRepository.findByTaskIdOrderBySortOrder(taskId);
    var existingIds = existingItems.stream().map(TaskItem::getId).toList();

    // Validate that orderedIds matches the existing items exactly
    if (orderedIds.size() != existingIds.size()
        || !orderedIds.containsAll(existingIds)
        || !existingIds.containsAll(orderedIds)) {
      throw new InvalidStateException(
          "Invalid reorder", "Ordered IDs must match existing task item IDs exactly");
    }

    // Update sort orders
    for (int i = 0; i < orderedIds.size(); i++) {
      UUID itemId = orderedIds.get(i);
      var item =
          existingItems.stream()
              .filter(it -> it.getId().equals(itemId))
              .findFirst()
              .orElseThrow(() -> new ResourceNotFoundException("TaskItem", itemId));
      item.update(item.getTitle(), i);
    }
    taskItemRepository.saveAll(existingItems);

    log.info("Reordered {} task items on task {}", orderedIds.size(), taskId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("task_item.reordered")
            .entityType("task")
            .entityId(taskId)
            .details(Map.of("item_count", orderedIds.size()))
            .build());

    return taskItemRepository.findByTaskIdOrderBySortOrder(taskId);
  }
}
