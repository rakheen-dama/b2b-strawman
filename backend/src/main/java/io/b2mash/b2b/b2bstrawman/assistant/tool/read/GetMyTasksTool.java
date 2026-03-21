package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.mywork.MyWorkController.MyWorkTaskItem;
import io.b2mash.b2b.b2bstrawman.mywork.MyWorkService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GetMyTasksTool implements AssistantTool {

  private final MyWorkService myWorkService;

  public GetMyTasksTool(MyWorkService myWorkService) {
    this.myWorkService = myWorkService;
  }

  @Override
  public String name() {
    return "get_my_tasks";
  }

  @Override
  public String description() {
    return "Get the current user's tasks across all projects, grouped by assigned and unassigned.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties", Map.of(),
        "required", List.of());
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
    var response = myWorkService.getMyTasks(context.memberId(), null, null, null);

    return Map.of(
        "assigned", response.assigned().stream().map(this::mapTaskItem).toList(),
        "unassigned", response.unassigned().stream().map(this::mapTaskItem).toList());
  }

  private Map<String, Object> mapTaskItem(MyWorkTaskItem item) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", item.id().toString());
    map.put("title", item.title());
    map.put("status", item.status());
    map.put("priority", item.priority());
    map.put("projectId", item.projectId() != null ? item.projectId().toString() : null);
    map.put("projectName", item.projectName());
    map.put("dueDate", item.dueDate() != null ? item.dueDate().toString() : null);
    map.put("totalTimeMinutes", item.totalTimeMinutes());
    return map;
  }
}
