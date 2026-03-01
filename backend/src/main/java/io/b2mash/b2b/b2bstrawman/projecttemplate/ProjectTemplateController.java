package io.b2mash.b2b.b2bstrawman.projecttemplate;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.dto.PrerequisiteCheckResponse;
import io.b2mash.b2b.b2bstrawman.project.ProjectController;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.ProjectTemplateResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.SaveFromProjectRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.UpdateTemplateRequest;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-templates")
public class ProjectTemplateController {

  private final ProjectTemplateService projectTemplateService;

  public ProjectTemplateController(ProjectTemplateService projectTemplateService) {
    this.projectTemplateService = projectTemplateService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectTemplateResponse>> listTemplates() {
    return ResponseEntity.ok(projectTemplateService.list());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> getTemplate(@PathVariable UUID id) {
    return ResponseEntity.ok(projectTemplateService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> createTemplate(
      @Valid @RequestBody CreateTemplateRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = projectTemplateService.create(request, memberId);
    return ResponseEntity.created(URI.create("/api/project-templates/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
    return ResponseEntity.ok(projectTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
    projectTemplateService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/duplicate")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> duplicateTemplate(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = projectTemplateService.duplicate(id, memberId);
    return ResponseEntity.created(URI.create("/api/project-templates/" + response.id()))
        .body(response);
  }

  @PostMapping("/from-project/{projectId}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> saveFromProject(
      @PathVariable UUID projectId, @Valid @RequestBody SaveFromProjectRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var response = projectTemplateService.saveFromProject(projectId, request, memberId, orgRole);
    return ResponseEntity.created(URI.create("/api/project-templates/" + response.id()))
        .body(response);
  }

  @PostMapping("/{id}/instantiate")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectController.ProjectResponse> instantiateTemplate(
      @PathVariable UUID id, @Valid @RequestBody InstantiateTemplateRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var project = projectTemplateService.instantiateTemplate(id, request, memberId);
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectController.ProjectResponse.from(project));
  }

  @PutMapping("/{id}/required-customer-fields")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectTemplateResponse> updateRequiredCustomerFields(
      @PathVariable UUID id, @RequestBody UpdateRequiredFieldsRequest request) {
    return ResponseEntity.ok(
        projectTemplateService.updateRequiredCustomerFields(id, request.fieldDefinitionIds()));
  }

  @GetMapping("/{id}/prerequisite-check")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PrerequisiteCheckResponse> checkPrerequisites(
      @PathVariable UUID id, @RequestParam UUID customerId) {
    return ResponseEntity.ok(
        PrerequisiteCheckResponse.from(
            projectTemplateService.checkEngagementPrerequisites(customerId, id)));
  }

  public record UpdateRequiredFieldsRequest(List<UUID> fieldDefinitionIds) {}
}
