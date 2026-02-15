package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.FieldDefinitionResponse;
import io.b2mash.b2b.b2bstrawman.fielddefinition.dto.SetFieldGroupsRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectResponse>> listProjects(
      @RequestParam(required = false) Map<String, String> allParams) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var projects =
        projectService.listProjects(memberId, orgRole).stream()
            .map(pwr -> ProjectResponse.from(pwr.project(), pwr.projectRole()))
            .toList();

    // Apply custom field filtering if present
    Map<String, String> customFieldFilters = extractCustomFieldFilters(allParams);
    if (!customFieldFilters.isEmpty()) {
      projects =
          projects.stream()
              .filter(p -> matchesCustomFieldFilters(p.customFields(), customFieldFilters))
              .toList();
    }

    return ResponseEntity.ok(projects);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr = projectService.getProject(id, memberId, orgRole);
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole()));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var project =
        projectService.createProject(
            request.name(),
            request.description(),
            createdBy,
            request.customFields(),
            request.appliedFieldGroups());
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project, Roles.PROJECT_LEAD));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr =
        projectService.updateProject(
            id,
            request.name(),
            request.description(),
            memberId,
            orgRole,
            request.customFields(),
            request.appliedFieldGroups());
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/field-groups")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<FieldDefinitionResponse>> setFieldGroups(
      @PathVariable UUID id, @Valid @RequestBody SetFieldGroupsRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var fieldDefs =
        projectService.setFieldGroups(id, request.appliedFieldGroups(), memberId, orgRole);
    return ResponseEntity.ok(fieldDefs);
  }

  private Map<String, String> extractCustomFieldFilters(Map<String, String> allParams) {
    var filters = new HashMap<String, String>();
    if (allParams != null) {
      allParams.forEach(
          (key, value) -> {
            if (key.startsWith("customField[") && key.endsWith("]")) {
              String slug = key.substring("customField[".length(), key.length() - 1);
              filters.put(slug, value);
            }
          });
    }
    return filters;
  }

  private boolean matchesCustomFieldFilters(
      Map<String, Object> customFields, Map<String, String> filters) {
    if (customFields == null) {
      return filters.isEmpty();
    }
    for (var entry : filters.entrySet()) {
      Object fieldValue = customFields.get(entry.getKey());
      if (fieldValue == null || !fieldValue.toString().equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  public record CreateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record UpdateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters") String description,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {}

  public record ProjectResponse(
      UUID id,
      String name,
      String description,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      String projectRole,
      Map<String, Object> customFields,
      List<UUID> appliedFieldGroups) {

    public static ProjectResponse from(Project project) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          null,
          project.getCustomFields(),
          project.getAppliedFieldGroups());
    }

    public static ProjectResponse from(Project project, String projectRole) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          projectRole,
          project.getCustomFields(),
          project.getAppliedFieldGroups());
    }
  }
}
