package io.b2mash.b2b.b2bstrawman.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    var projects = projectService.listProjects().stream().map(ProjectResponse::from).toList();
    return ResponseEntity.ok(projects);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> getProject(@PathVariable UUID id) {
    return projectService
        .getProject(id)
        .map(project -> ResponseEntity.ok(ProjectResponse.from(project)))
        .orElseGet(() -> ResponseEntity.of(notFound(id)).build());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request, JwtAuthenticationToken auth) {
    String createdBy = auth.getName();
    var project = projectService.createProject(request.name(), request.description(), createdBy);
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    return projectService
        .updateProject(id, request.name(), request.description())
        .map(project -> ResponseEntity.ok(ProjectResponse.from(project)))
        .orElseGet(() -> ResponseEntity.of(notFound(id)).build());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    if (projectService.deleteProject(id)) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.of(ProblemDetail.forStatus(404)).build();
  }

  private ProblemDetail notFound(UUID id) {
    var problem = ProblemDetail.forStatus(404);
    problem.setTitle("Project not found");
    problem.setDetail("No project found with id " + id);
    return problem;
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
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {

    public static ProjectResponse from(Project project) {
      return new ProjectResponse(
          project.getId(),
          project.getName(),
          project.getDescription(),
          project.getCreatedBy(),
          project.getCreatedAt(),
          project.getUpdatedAt());
    }
  }
}
