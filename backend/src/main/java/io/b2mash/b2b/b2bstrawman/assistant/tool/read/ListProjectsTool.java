package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ListProjectsTool implements AssistantTool {

  private final ProjectService projectService;

  public ListProjectsTool(ProjectService projectService) {
    this.projectService = projectService;
  }

  @Override
  public String name() {
    return "list_projects";
  }

  @Override
  public String description() {
    return "List all projects for the current organization, with optional status and customer filters.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "status",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Filter by project status: ACTIVE, COMPLETED, or ARCHIVED"),
                "customerId", Map.of("type", "string", "description", "Filter by customer UUID")),
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
    var actor = new ActorContext(context.memberId(), context.orgRole());
    var statusFilter = (String) input.get("status");
    var customerIdFilter = (String) input.get("customerId");

    var projects = projectService.listProjects(actor);
    var stream = projects.stream();

    if (statusFilter != null && !statusFilter.isBlank()) {
      stream =
          stream.filter(pwr -> pwr.project().getStatus().name().equalsIgnoreCase(statusFilter));
    }
    if (customerIdFilter != null && !customerIdFilter.isBlank()) {
      UUID customerId;
      try {
        customerId = UUID.fromString(customerIdFilter);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid customerId format: " + customerIdFilter);
      }
      stream = stream.filter(pwr -> customerId.equals(pwr.project().getCustomerId()));
    }

    return stream
        .map(
            pwr -> {
              var p = pwr.project();
              var map = new LinkedHashMap<String, Object>();
              map.put("id", p.getId().toString());
              map.put("name", p.getName());
              map.put("status", p.getStatus().name());
              map.put(
                  "customerId", p.getCustomerId() != null ? p.getCustomerId().toString() : null);
              return map;
            })
        .toList();
  }
}
