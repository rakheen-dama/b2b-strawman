package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

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
public class UpdateProjectTool implements AssistantTool {

  private final ProjectService projectService;

  public UpdateProjectTool(ProjectService projectService) {
    this.projectService = projectService;
  }

  @Override
  public String name() {
    return "update_project";
  }

  @Override
  public String description() {
    return "Update an existing project's name, description, or customer link.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of("type", "string", "description", "UUID of the project to update"),
                "name", Map.of("type", "string", "description", "New project name"),
                "status",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "New project status: ACTIVE, COMPLETED, or ARCHIVED"),
                "customerId",
                    Map.of("type", "string", "description", "UUID of the customer to link")),
        "required", List.of("projectId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("PROJECT_MANAGEMENT");
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

    var name = (String) input.get("name");
    var customerIdStr = (String) input.get("customerId");

    UUID customerId = null;
    if (customerIdStr != null && !customerIdStr.isBlank()) {
      try {
        customerId = UUID.fromString(customerIdStr);
      } catch (IllegalArgumentException e) {
        return Map.of("error", "Invalid customerId format: " + customerIdStr);
      }
    }

    var actor = new ActorContext(context.memberId(), context.orgRole());
    var projectWithRole =
        projectService.updateProject(projectId, name, null, actor, null, null, customerId, null);
    var project = projectWithRole.project();

    var result = new LinkedHashMap<String, Object>();
    result.put("id", project.getId().toString());
    result.put("name", project.getName());
    result.put("status", project.getStatus().name());
    result.put(
        "customerId", project.getCustomerId() != null ? project.getCustomerId().toString() : null);
    return result;
  }
}
