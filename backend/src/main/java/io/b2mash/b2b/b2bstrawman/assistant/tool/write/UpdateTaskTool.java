package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

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
public class UpdateTaskTool implements AssistantTool {

  private final TaskService taskService;

  public UpdateTaskTool(TaskService taskService) {
    this.taskService = taskService;
  }

  @Override
  public String name() {
    return "update_task";
  }

  @Override
  public String description() {
    return "Update an existing task's title, status, or assignee.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "taskId", Map.of("type", "string", "description", "UUID of the task to update"),
                "title", Map.of("type", "string", "description", "New task title"),
                "status",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "New task status: OPEN, IN_PROGRESS, DONE, or CANCELLED"),
                "assigneeId",
                    Map.of("type", "string", "description", "UUID of the member to assign")),
        "required", List.of("taskId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of();
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var taskIdStr = (String) input.get("taskId");
    UUID taskId;
    try {
      taskId = UUID.fromString(taskIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid taskId format: " + taskIdStr);
    }

    var title = (String) input.get("title");
    var status = (String) input.get("status");
    var assigneeIdStr = (String) input.get("assigneeId");

    UUID assigneeId = null;
    if (assigneeIdStr != null && !assigneeIdStr.isBlank()) {
      try {
        assigneeId = UUID.fromString(assigneeIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid assigneeId format: " + assigneeIdStr);
      }
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var task =
        taskService.updateTask(taskId, title, null, null, status, null, null, assigneeId, actor);

    var result = new LinkedHashMap<String, Object>();
    result.put("id", task.getId().toString());
    result.put("title", task.getTitle());
    result.put("status", task.getStatus().name());
    result.put("assigneeId", task.getAssigneeId() != null ? task.getAssigneeId().toString() : null);
    return result;
  }
}
