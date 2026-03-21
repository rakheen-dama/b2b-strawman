package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ListTasksTool implements AssistantTool {

  private final TaskService taskService;

  public ListTasksTool(TaskService taskService) {
    this.taskService = taskService;
  }

  @Override
  public String name() {
    return "list_tasks";
  }

  @Override
  public String description() {
    return "List tasks for a specific project, with optional status and assignee filters.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of("type", "string", "description", "UUID of the project (required)"),
                "status",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Filter by task status: OPEN, IN_PROGRESS, DONE, CANCELLED"),
                "assigneeId",
                    Map.of("type", "string", "description", "Filter by assignee member UUID")),
        "required", List.of("projectId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var projectIdStr = (String) input.get("projectId");
    if (projectIdStr == null || projectIdStr.isBlank()) {
      return Map.of("error", "projectId is required");
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());

    UUID projectId;
    try {
      projectId = UUID.fromString(projectIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid projectId format: " + projectIdStr);
    }

    var statusFilter = (String) input.get("status");
    var assigneeIdStr = (String) input.get("assigneeId");
    UUID assigneeId = null;
    if (assigneeIdStr != null && !assigneeIdStr.isBlank()) {
      try {
        assigneeId = UUID.fromString(assigneeIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid assigneeId format: " + assigneeIdStr);
      }
    }

    var tasks = taskService.listTasks(projectId, actor, statusFilter, assigneeId, null, null);

    return tasks.stream()
        .map(
            t -> {
              var map = new LinkedHashMap<String, Object>();
              map.put("id", t.getId().toString());
              map.put("title", t.getTitle());
              map.put("status", t.getStatus().name());
              map.put("priority", t.getPriority().name());
              map.put(
                  "assigneeId", t.getAssigneeId() != null ? t.getAssigneeId().toString() : null);
              map.put("projectId", t.getProjectId().toString());
              map.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
              return map;
            })
        .toList();
  }
}
