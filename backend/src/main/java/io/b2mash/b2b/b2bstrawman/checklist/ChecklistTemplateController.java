package io.b2mash.b2b.b2bstrawman.checklist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    var templates = checklistTemplateService.listActive();
    if (customerType != null && !customerType.isBlank()) {
      templates =
          templates.stream()
              .filter(t -> t.customerType().equals(customerType) || t.customerType().equals("ANY"))
              .toList();
    }
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

  // --- DTOs ---

  public record ChecklistTemplateItemRequest(
      @NotBlank String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      UUID dependsOnItemId) {}

  public record CreateChecklistTemplateRequest(
      @NotBlank String name,
      String description,
      @NotBlank String customerType,
      boolean autoInstantiate,
      String slug,
      List<ChecklistTemplateItemRequest> items) {}

  public record UpdateChecklistTemplateRequest(
      @NotBlank String name,
      String description,
      boolean autoInstantiate,
      List<ChecklistTemplateItemRequest> items) {}

  public record ChecklistTemplateItemResponse(
      UUID id,
      UUID templateId,
      String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      String requiredDocumentLabel,
      UUID dependsOnItemId,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistTemplateItemResponse from(ChecklistTemplateItem item) {
      return new ChecklistTemplateItemResponse(
          item.getId(),
          item.getTemplateId(),
          item.getName(),
          item.getDescription(),
          item.getSortOrder(),
          item.isRequired(),
          item.isRequiresDocument(),
          item.getRequiredDocumentLabel(),
          item.getDependsOnItemId(),
          item.getCreatedAt(),
          item.getUpdatedAt());
    }
  }

  public record ChecklistTemplateResponse(
      UUID id,
      String name,
      String slug,
      String description,
      String customerType,
      String source,
      String packId,
      boolean active,
      boolean autoInstantiate,
      int sortOrder,
      List<ChecklistTemplateItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {

    public static ChecklistTemplateResponse from(
        ChecklistTemplate t, List<ChecklistTemplateItem> items) {
      return new ChecklistTemplateResponse(
          t.getId(),
          t.getName(),
          t.getSlug(),
          t.getDescription(),
          t.getCustomerType(),
          t.getSource(),
          t.getPackId(),
          t.isActive(),
          t.isAutoInstantiate(),
          t.getSortOrder(),
          items.stream().map(ChecklistTemplateItemResponse::from).toList(),
          t.getCreatedAt(),
          t.getUpdatedAt());
    }
  }
}
