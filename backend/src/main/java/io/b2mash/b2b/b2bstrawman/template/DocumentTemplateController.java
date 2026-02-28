package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.setupstatus.DocumentGenerationReadinessService;
import io.b2mash.b2b.b2bstrawman.setupstatus.TemplateReadiness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
  private final PdfRenderingService pdfRenderingService;
  private final GeneratedDocumentService generatedDocumentService;
  private final DocumentGenerationReadinessService documentGenerationReadinessService;
  private final VariableMetadataRegistry variableMetadataRegistry;

  public DocumentTemplateController(
      DocumentTemplateService documentTemplateService,
      PdfRenderingService pdfRenderingService,
      GeneratedDocumentService generatedDocumentService,
      DocumentGenerationReadinessService documentGenerationReadinessService,
      VariableMetadataRegistry variableMetadataRegistry) {
    this.documentTemplateService = documentTemplateService;
    this.pdfRenderingService = pdfRenderingService;
    this.generatedDocumentService = generatedDocumentService;
    this.documentGenerationReadinessService = documentGenerationReadinessService;
    this.variableMetadataRegistry = variableMetadataRegistry;
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

  @GetMapping("/readiness")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER', 'ORG_MEMBER')")
  public ResponseEntity<List<TemplateReadiness>> getReadiness(
      @RequestParam TemplateEntityType entityType, @RequestParam UUID entityId) {
    return ResponseEntity.ok(
        documentGenerationReadinessService.checkReadiness(entityType, entityId));
  }

  @GetMapping("/variables")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<VariableMetadataRegistry.VariableMetadataResponse> getVariables(
      @RequestParam TemplateEntityType entityType) {
    return ResponseEntity.ok(variableMetadataRegistry.getVariables(entityType));
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
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PdfRenderingService.PreviewResponse> previewTemplate(
      @PathVariable UUID id, @Valid @RequestBody PreviewRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    return ResponseEntity.ok(
        generatedDocumentService.previewDocument(
            id, request.entityId(), request.clauses(), memberId));
  }

  @PostMapping("/{id}/generate")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> generateDocument(
      @PathVariable UUID id, @Valid @RequestBody GenerateDocumentRequest request) {
    UUID memberId = RequestScopes.requireMemberId();

    var result =
        generatedDocumentService.generateDocument(
            id,
            request.entityId(),
            request.saveToDocuments(),
            request.acknowledgeWarnings(),
            request.clauses(),
            memberId);

    var generatedDoc = result.generatedDocument();
    var pdfResult = result.pdfResult();

    if (!request.saveToDocuments()) {
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + pdfResult.fileName() + "\"")
          .body(pdfResult.pdfBytes());
    } else {
      return ResponseEntity.created(URI.create("/api/generated-documents/" + generatedDoc.getId()))
          .body(
              new GenerateDocumentResponse(
                  generatedDoc.getId(),
                  generatedDoc.getFileName(),
                  generatedDoc.getFileSize(),
                  generatedDoc.getDocumentId(),
                  generatedDoc.getGeneratedAt()));
    }
  }

  // --- DTOs ---

  /** Clause selection from the client, specifying a clause ID and its desired sort order. */
  public record ClauseSelection(@NotNull UUID clauseId, int sortOrder) {}

  public record PreviewRequest(@NotNull UUID entityId, @Valid List<ClauseSelection> clauses) {}

  // PreviewResponse lives in PdfRenderingService to keep the dependency direction correct.

  public record GenerateDocumentRequest(
      @NotNull UUID entityId,
      boolean saveToDocuments,
      boolean acknowledgeWarnings,
      @Valid List<ClauseSelection> clauses) {}

  public record GenerateDocumentResponse(
      UUID id, String fileName, long fileSize, UUID documentId, Instant generatedAt) {}

  public record CreateTemplateRequest(
      @NotBlank String name,
      String description,
      @NotNull TemplateCategory category,
      @NotNull TemplateEntityType primaryEntityType,
      @NotNull Map<String, Object> content,
      String css,
      String slug,
      List<Map<String, String>> requiredContextFields) {}

  public record UpdateTemplateRequest(
      @NotBlank String name,
      String description,
      @NotNull Map<String, Object> content,
      String css,
      Integer sortOrder,
      List<Map<String, String>> requiredContextFields) {}

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
      Map<String, Object> content,
      String legacyContent,
      String css,
      String source,
      UUID sourceTemplateId,
      String packId,
      String packTemplateKey,
      boolean active,
      int sortOrder,
      List<Map<String, String>> requiredContextFields,
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
          dt.getLegacyContent(),
          dt.getCss(),
          dt.getSource().name(),
          dt.getSourceTemplateId(),
          dt.getPackId(),
          dt.getPackTemplateKey(),
          dt.isActive(),
          dt.getSortOrder(),
          dt.getRequiredContextFields(),
          dt.getCreatedAt(),
          dt.getUpdatedAt());
    }
  }
}
