package io.b2mash.b2b.b2bstrawman.projecttemplate;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.prerequisite.dto.PrerequisiteCheckResponse;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.MatterTemplateResponse;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.SaveFromMatterRequest;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.UpdateTemplateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/matter-templates")
public class ProjectTemplateController {

  private final ProjectTemplateService projectTemplateService;
  private final PrerequisiteService prerequisiteService;

  public ProjectTemplateController(
      ProjectTemplateService projectTemplateService, PrerequisiteService prerequisiteService) {
    this.projectTemplateService = projectTemplateService;
    this.prerequisiteService = prerequisiteService;
  }

  @GetMapping
  public ResponseEntity<List<MatterTemplateResponse>> listTemplates() {
    return ResponseEntity.ok(projectTemplateService.list());
  }

  @GetMapping("/{id}")
  public ResponseEntity<MatterTemplateResponse> getTemplate(@PathVariable UUID id) {
    return ResponseEntity.ok(projectTemplateService.get(id));
  }

  @PostMapping
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterTemplateResponse> createTemplate(
      @Valid @RequestBody CreateTemplateRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = projectTemplateService.create(request, memberId);
    return ResponseEntity.created(URI.create("/api/matter-templates/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterTemplateResponse> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
    return ResponseEntity.ok(projectTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
    projectTemplateService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/duplicate")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterTemplateResponse> duplicateTemplate(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = projectTemplateService.duplicate(id, memberId);
    return ResponseEntity.created(URI.create("/api/matter-templates/" + response.id()))
        .body(response);
  }

  @PostMapping("/from-matter/{matterId}")
  public ResponseEntity<MatterTemplateResponse> saveFromProject(
      @PathVariable UUID matterId,
      @Valid @RequestBody SaveFromMatterRequest request,
      ActorContext actor) {
    var response = projectTemplateService.saveFromProject(matterId, request, actor);
    return ResponseEntity.created(URI.create("/api/matter-templates/" + response.id()))
        .body(response);
  }

  @PostMapping("/{id}/instantiate")
  public ResponseEntity<MatterResponse> instantiateTemplate(
      @PathVariable UUID id, @Valid @RequestBody InstantiateTemplateRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var project = projectTemplateService.instantiateTemplate(id, request, memberId);
    return ResponseEntity.created(URI.create("/api/matters/" + project.getId()))
        .body(MatterResponse.from(project));
  }

  @PutMapping("/{id}/required-client-fields")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<MatterTemplateResponse> updateRequiredCustomerFields(
      @PathVariable UUID id, @RequestBody UpdateRequiredFieldsRequest request) {
    return ResponseEntity.ok(
        projectTemplateService.updateRequiredCustomerFields(id, request.fieldDefinitionIds()));
  }

  @GetMapping("/{id}/prerequisite-check")
  public ResponseEntity<PrerequisiteCheckResponse> checkPrerequisites(
      @PathVariable UUID id, @RequestParam UUID customerId) {
    return ResponseEntity.ok(
        PrerequisiteCheckResponse.from(
            prerequisiteService.checkEngagementPrerequisites(customerId, id)));
  }

  public record UpdateRequiredFieldsRequest(List<UUID> fieldDefinitionIds) {}
}
