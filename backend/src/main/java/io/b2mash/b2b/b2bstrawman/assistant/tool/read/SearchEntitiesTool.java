package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SearchEntitiesTool implements AssistantTool {

  private final ProjectService projectService;
  private final CustomerService customerService;
  private final TaskService taskService;

  public SearchEntitiesTool(
      ProjectService projectService, CustomerService customerService, TaskService taskService) {
    this.projectService = projectService;
    this.customerService = customerService;
    this.taskService = taskService;
  }

  @Override
  public String name() {
    return "search_entities";
  }

  @Override
  public String description() {
    return "Search across projects, customers, and tasks by name or title.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "query",
                Map.of(
                    "type",
                    "string",
                    "description",
                    "Search term to match against names and titles")),
        "required", List.of("query"));
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
    var query = (String) input.get("query");
    if (query == null || query.isBlank()) {
      return Map.of("error", "query is required");
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var lowerQuery = query.toLowerCase();

    // Search projects
    var matchingProjects =
        projectService.listProjects(actor).stream()
            .filter(pwr -> pwr.project().getName().toLowerCase().contains(lowerQuery))
            .map(
                pwr -> {
                  var p = pwr.project();
                  var map = new LinkedHashMap<String, Object>();
                  map.put("id", p.getId().toString());
                  map.put("name", p.getName());
                  map.put("status", p.getStatus().name());
                  map.put(
                      "customerId",
                      p.getCustomerId() != null ? p.getCustomerId().toString() : null);
                  return map;
                })
            .toList();

    // Search customers
    var matchingCustomers =
        customerService.listCustomers().stream()
            .filter(
                c ->
                    c.getName().toLowerCase().contains(lowerQuery)
                        || (c.getEmail() != null
                            && c.getEmail().toLowerCase().contains(lowerQuery)))
            .map(
                c -> {
                  var map = new LinkedHashMap<String, Object>();
                  map.put("id", c.getId().toString());
                  map.put("name", c.getName());
                  map.put("lifecycleStatus", c.getLifecycleStatus().name());
                  map.put("email", c.getEmail());
                  return map;
                })
            .toList();

    // Search tasks across matching projects
    var matchingTasks = new ArrayList<Map<String, Object>>();
    for (var pwr : projectService.listProjects(actor)) {
      try {
        var tasks = taskService.listTasks(pwr.project().getId(), actor, null, null, null, null);
        for (var t : tasks) {
          if (t.getTitle().toLowerCase().contains(lowerQuery)) {
            var map = new LinkedHashMap<String, Object>();
            map.put("id", t.getId().toString());
            map.put("title", t.getTitle());
            map.put("status", t.getStatus().name());
            map.put("projectId", t.getProjectId().toString());
            map.put("priority", t.getPriority().name());
            matchingTasks.add(map);
          }
        }
      } catch (Exception e) {
        // Skip projects where task listing fails (e.g., access denied)
      }
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("projects", matchingProjects);
    result.put("customers", matchingCustomers);
    result.put("tasks", matchingTasks);
    result.put(
        "totalResults", matchingProjects.size() + matchingCustomers.size() + matchingTasks.size());
    return result;
  }
}
