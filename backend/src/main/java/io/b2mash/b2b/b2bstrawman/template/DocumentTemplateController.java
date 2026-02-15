package io.b2mash.b2b.b2bstrawman.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/templates")
public class DocumentTemplateController {

  private final DocumentTemplateService documentTemplateService;

  public DocumentTemplateController(DocumentTemplateService documentTemplateService) {
    this.documentTemplateService = documentTemplateService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<TemplateListResponse>> listTemplates(
      @RequestParam(required = false) TemplateCategory category,
      @RequestParam(required = false) TemplateEntityType primaryEntityType) {
    List<TemplateListResponse> templates;
    if (category != null) {
      templates = documentTemplateService.listByCategory(category);
    } else if (primaryEntityType != null) {
      templates = documentTemplateService.listByEntityType(primaryEntityType);
    } else {
      templates = documentTemplateService.listAll();
    }
    return ResponseEntity.ok(templates);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TemplateDetailResponse> getTemplate(@PathVariable UUID id) {
    return ResponseEntity.ok(documentTemplateService.getById(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateDetailResponse> createTemplate(
      @Valid @RequestBody CreateTemplateRequest request) {
    var response = documentTemplateService.create(request);
    return ResponseEntity.created(URI.create("/api/templates/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateDetailResponse> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
    return ResponseEntity.ok(documentTemplateService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deactivateTemplate(@PathVariable UUID id) {
    documentTemplateService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/clone")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TemplateDetailResponse> cloneTemplate(@PathVariable UUID id) {
    var response = documentTemplateService.cloneTemplate(id);
    return ResponseEntity.created(URI.create("/api/templates/" + response.id())).body(response);
  }

  @PostMapping("/{id}/reset")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> resetTemplate(@PathVariable UUID id) {
    documentTemplateService.resetToDefault(id);
    return ResponseEntity.ok().build();
  }

  // --- DTOs ---

  public record CreateTemplateRequest(
      @NotBlank String name,
      String description,
      @NotNull TemplateCategory category,
      @NotNull TemplateEntityType primaryEntityType,
      @NotBlank String content,
      String css,
      String slug) {}

  public record UpdateTemplateRequest(
      @NotBlank String name,
      String description,
      @NotBlank String content,
      String css,
      Integer sortOrder) {}

  public record TemplateListResponse(
      UUID id,
      String name,
      String slug,
      String description,
      String category,
      String primaryEntityType,
      String source,
      UUID sourceTemplateId,
      boolean active,
      int sortOrder,
      Instant createdAt,
      Instant updatedAt) {

    public static TemplateListResponse from(DocumentTemplate dt) {
      return new TemplateListResponse(
          dt.getId(),
          dt.getName(),
          dt.getSlug(),
          dt.getDescription(),
          dt.getCategory().name(),
          dt.getPrimaryEntityType().name(),
          dt.getSource().name(),
          dt.getSourceTemplateId(),
          dt.isActive(),
          dt.getSortOrder(),
          dt.getCreatedAt(),
          dt.getUpdatedAt());
    }
  }

  public record TemplateDetailResponse(
      UUID id,
      String name,
      String slug,
      String description,
      String category,
      String primaryEntityType,
      String content,
      String css,
      String source,
      UUID sourceTemplateId,
      String packId,
      String packTemplateKey,
      boolean active,
      int sortOrder,
      Instant createdAt,
      Instant updatedAt) {

    public static TemplateDetailResponse from(DocumentTemplate dt) {
      return new TemplateDetailResponse(
          dt.getId(),
          dt.getName(),
          dt.getSlug(),
          dt.getDescription(),
          dt.getCategory().name(),
          dt.getPrimaryEntityType().name(),
          dt.getContent(),
          dt.getCss(),
          dt.getSource().name(),
          dt.getSourceTemplateId(),
          dt.getPackId(),
          dt.getPackTemplateKey(),
          dt.isActive(),
          dt.getSortOrder(),
          dt.getCreatedAt(),
          dt.getUpdatedAt());
    }
  }
}
