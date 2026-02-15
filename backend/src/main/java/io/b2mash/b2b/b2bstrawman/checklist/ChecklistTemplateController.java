package io.b2mash.b2b.b2bstrawman.checklist;

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
@RequestMapping("/api/checklist-templates")
public class ChecklistTemplateController {

  private final ChecklistTemplateService checklistTemplateService;

  public ChecklistTemplateController(ChecklistTemplateService checklistTemplateService) {
    this.checklistTemplateService = checklistTemplateService;
  }

  @GetMapping
  public ResponseEntity<List<TemplateResponse>> list() {
    return ResponseEntity.ok(checklistTemplateService.listAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<TemplateWithItemsResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(checklistTemplateService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateWithItemsResponse> create(
      @Valid @RequestBody CreateTemplateRequest request) {
    var response = checklistTemplateService.create(request);
    return ResponseEntity.created(
            URI.create("/api/checklist-templates/" + response.template().id()))
        .body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateWithItemsResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
    return ResponseEntity.ok(checklistTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    checklistTemplateService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/clone")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateWithItemsResponse> clone(
      @PathVariable UUID id, @Valid @RequestBody CloneTemplateRequest request) {
    var response = checklistTemplateService.clone(id, request.newName());
    return ResponseEntity.created(
            URI.create("/api/checklist-templates/" + response.template().id()))
        .body(response);
  }

  // --- DTOs ---

  public record CreateTemplateRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 200) String slug,
      String description,
      @NotBlank @Size(max = 20) String customerType,
      boolean autoInstantiate,
      int sortOrder,
      List<CreateItemRequest> items) {}

  public record CreateItemRequest(
      @NotBlank @Size(max = 300) String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      @Size(max = 200) String requiredDocumentLabel,
      UUID dependsOnItemId) {}

  public record UpdateTemplateRequest(
      @NotBlank @Size(max = 200) String name,
      String description,
      boolean autoInstantiate,
      int sortOrder,
      List<UpdateItemRequest> items) {}

  public record UpdateItemRequest(
      UUID id,
      @NotBlank @Size(max = 300) String name,
      String description,
      int sortOrder,
      boolean required,
      boolean requiresDocument,
      @Size(max = 200) String requiredDocumentLabel,
      UUID dependsOnItemId) {}

  public record CloneTemplateRequest(@NotBlank @Size(max = 200) String newName) {}

  public record TemplateResponse(
      UUID id,
      String name,
      String slug,
      String description,
      String customerType,
      String source,
      String packId,
      String packTemplateKey,
      boolean active,
      boolean autoInstantiate,
      int sortOrder,
      Instant createdAt,
      Instant updatedAt) {

    public static TemplateResponse from(ChecklistTemplate t) {
      return new TemplateResponse(
          t.getId(),
          t.getName(),
          t.getSlug(),
          t.getDescription(),
          t.getCustomerType(),
          t.getSource(),
          t.getPackId(),
          t.getPackTemplateKey(),
          t.isActive(),
          t.isAutoInstantiate(),
          t.getSortOrder(),
          t.getCreatedAt(),
          t.getUpdatedAt());
    }
  }

  public record TemplateWithItemsResponse(
      TemplateResponse template, List<TemplateItemResponse> items) {}

  public record TemplateItemResponse(
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

    public static TemplateItemResponse from(ChecklistTemplateItem item) {
      return new TemplateItemResponse(
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
}
