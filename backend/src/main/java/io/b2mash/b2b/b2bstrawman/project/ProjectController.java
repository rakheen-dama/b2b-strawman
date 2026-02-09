package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
  public ResponseEntity<List<ProjectResponse>> listProjects() {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var projects =
        projectService.listProjects(memberId, orgRole).stream()
            .map(pwr -> ProjectResponse.from(pwr.project(), pwr.projectRole()))
            .toList();
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
    var project = projectService.createProject(request.name(), request.description(), createdBy);
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
        projectService.updateProject(id, request.name(), request.description(), memberId, orgRole);
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }

  public record CreateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters")
          String description) {}

  public record UpdateProjectRequest(
      @NotBlank(message = "name is required")
          @Size(max = 255, message = "name must be at most 255 characters")
          String name,
      @Size(max = 2000, message = "description must be at most 2000 characters")
          String description) {}

  public record ProjectResponse(
      UUID id,
      String name,
      String description,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt,
      String projectRole) {

    public static ProjectResponse from(Project project) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          null);
    }

    public static ProjectResponse from(Project project, String projectRole) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt(),
          projectRole);
    }
  }
}
