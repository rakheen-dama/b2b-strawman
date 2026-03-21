package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreateProjectTool implements AssistantTool {

  private final ProjectService projectService;

  public CreateProjectTool(ProjectService projectService) {
    this.projectService = projectService;
  }

  @Override
  public String name() {
    return "create_project";
  }

  @Override
  public String description() {
    return "Create a new project in the current organization."
        + " Document templates are applied at generation time, not project creation.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "name", Map.of("type", "string", "description", "Name of the project"),
                "customerId",
                    Map.of("type", "string", "description", "UUID of the customer to link")),
        "required", List.of("name"));
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

    var project =
        projectService.createProject(name, null, context.memberId(), null, null, customerId, null);

    var result = new LinkedHashMap<String, Object>();
    result.put("id", project.getId().toString());
    result.put("name", project.getName());
    result.put("status", project.getStatus().name());
    return result;
  }
}
