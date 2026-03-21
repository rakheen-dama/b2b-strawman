package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.project.ProjectWithRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetProjectTool implements AssistantTool {

  private final ProjectService projectService;
  private final ProjectMemberRepository projectMemberRepository;

  public GetProjectTool(
      ProjectService projectService, ProjectMemberRepository projectMemberRepository) {
    this.projectService = projectService;
    this.projectMemberRepository = projectMemberRepository;
  }

  @Override
  public String name() {
    return "get_project";
  }

  @Override
  public String description() {
    return "Get detailed information about a specific project by ID or name.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "projectId",
                    Map.of("type", "string", "description", "UUID of the project to retrieve"),
                "projectName",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Name of the project (used if projectId is not provided)")),
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
    var projectId = (String) input.get("projectId");
    var projectName = (String) input.get("projectName");

    ProjectWithRole pwr;
    if (projectId != null && !projectId.isBlank()) {
      pwr = projectService.getProject(UUID.fromString(projectId), actor);
    } else if (projectName != null && !projectName.isBlank()) {
      pwr =
          projectService.listProjects(actor).stream()
              .filter(p -> p.project().getName().equalsIgnoreCase(projectName))
              .findFirst()
              .orElse(null);
    } else {
      return Map.of("error", "Either projectId or projectName is required");
    }

    if (pwr == null) {
      return Map.of("error", "Project not found");
    }

    var p = pwr.project();
    var memberCount = projectMemberRepository.findByProjectId(p.getId()).size();

    var result = new LinkedHashMap<String, Object>();
    result.put("id", p.getId().toString());
    result.put("name", p.getName());
    result.put("status", p.getStatus().name());
    result.put("description", p.getDescription());
    result.put("customerId", p.getCustomerId() != null ? p.getCustomerId().toString() : null);
    result.put("dueDate", p.getDueDate() != null ? p.getDueDate().toString() : null);
    result.put("createdAt", p.getCreatedAt().toString());
    result.put("memberCount", memberCount);
    return result;
  }
}
