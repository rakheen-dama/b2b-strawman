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
public class CreateTaskTool implements AssistantTool {

  private final TaskService taskService;

  public CreateTaskTool(TaskService taskService) {
    this.taskService = taskService;
  }

  @Override
  public String name() {
    return "create_task";
  }

  @Override
  public String description() {
    return "Create a new task within a project.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId", Map.of("type", "string", "description", "UUID of the project"),
                "title", Map.of("type", "string", "description", "Task title"),
                "description", Map.of("type", "string", "description", "Task description"),
                "assigneeId",
                    Map.of("type", "string", "description", "UUID of the member to assign")),
        "required", List.of("projectId", "title"));
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
    var projectIdStr = (String) input.get("projectId");
    UUID projectId;
    try {
      projectId = UUID.fromString(projectIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid projectId format: " + projectIdStr);
    }

    var title = (String) input.get("title");
    var description = (String) input.get("description");
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
        taskService.createTask(
            projectId, title, description, "MEDIUM", "TASK", null, actor, null, null, assigneeId);

    var result = new LinkedHashMap<String, Object>();
    result.put("id", task.getId().toString());
    result.put("title", task.getTitle());
    result.put("status", task.getStatus().name());
    return result;
  }
}
