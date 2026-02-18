package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.ChecklistTemplateResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.CreateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.UpdateChecklistTemplateRequest;
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
@RequestMapping("/api/checklist-templates")
public class ChecklistTemplateController {

  private final ChecklistTemplateService checklistTemplateService;

  public ChecklistTemplateController(ChecklistTemplateService checklistTemplateService) {
    this.checklistTemplateService = checklistTemplateService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<ChecklistTemplateResponse>> listTemplates(
      @RequestParam(required = false) String customerType) {
    var templates = checklistTemplateService.listActive(customerType);
    return ResponseEntity.ok(templates);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ChecklistTemplateResponse> getTemplate(@PathVariable UUID id) {
    return ResponseEntity.ok(checklistTemplateService.getById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistTemplateResponse> createTemplate(
      @Valid @RequestBody CreateChecklistTemplateRequest request) {
    var response = checklistTemplateService.create(request);
    return ResponseEntity.created(URI.create("/api/checklist-templates/" + response.id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ChecklistTemplateResponse> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateChecklistTemplateRequest request) {
    return ResponseEntity.ok(checklistTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deactivateTemplate(@PathVariable UUID id) {
    checklistTemplateService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
