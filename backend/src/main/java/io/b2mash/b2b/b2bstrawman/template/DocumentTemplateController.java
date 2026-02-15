package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.config.S3Config.S3Properties;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RestController
@RequestMapping("/api/templates")
public class DocumentTemplateController {

  private final DocumentTemplateService documentTemplateService;
  private final PdfRenderingService pdfRenderingService;
  private final GeneratedDocumentService generatedDocumentService;
  private final S3Client s3Client;
  private final S3Properties s3Properties;
  private final DocumentRepository documentRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberRepository memberRepository;

  public DocumentTemplateController(
      DocumentTemplateService documentTemplateService,
      PdfRenderingService pdfRenderingService,
      GeneratedDocumentService generatedDocumentService,
      S3Client s3Client,
      S3Properties s3Properties,
      DocumentRepository documentRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberRepository memberRepository) {
    this.documentTemplateService = documentTemplateService;
    this.pdfRenderingService = pdfRenderingService;
    this.generatedDocumentService = generatedDocumentService;
    this.s3Client = s3Client;
    this.s3Properties = s3Properties;
    this.documentRepository = documentRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberRepository = memberRepository;
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
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<String> previewTemplate(
      @PathVariable UUID id, @Valid @RequestBody PreviewRequest request) {
    UUID memberId = RequestScopes.MEMBER_ID.get();
    var result = pdfRenderingService.generatePdf(id, request.entityId(), memberId);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .header("Content-Security-Policy", "sandbox")
        .body(result.htmlPreview());
  }

  @PostMapping("/{id}/generate")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> generateDocument(
      @PathVariable UUID id, @Valid @RequestBody GenerateDocumentRequest request) {
    UUID memberId = RequestScopes.MEMBER_ID.get();
    String orgRole = RequestScopes.getOrgRole();

    // 1. Generate PDF
    var pdfResult = pdfRenderingService.generatePdf(id, request.entityId(), memberId);

    // 2. Load template for metadata
    var templateDetail = documentTemplateService.getById(id);

    // 3. Upload to S3
    String tenantId = RequestScopes.TENANT_ID.get();
    String s3Key = "org/" + tenantId + "/generated/" + pdfResult.fileName();

    try {
      var putRequest =
          PutObjectRequest.builder()
              .bucket(s3Properties.bucketName())
              .key(s3Key)
              .contentType("application/pdf")
              .build();
      s3Client.putObject(
          putRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(pdfResult.pdfBytes()));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to upload generated PDF to S3", e);
    }

    // 4. Build context snapshot
    var contextSnapshot = buildContextSnapshot(templateDetail, request.entityId());

    // 5. Create GeneratedDocument record
    var generatedDoc =
        generatedDocumentService.create(
            id,
            TemplateEntityType.valueOf(templateDetail.primaryEntityType()),
            request.entityId(),
            pdfResult.fileName(),
            s3Key,
            pdfResult.pdfBytes().length,
            memberId,
            contextSnapshot);

    // 6. Optionally save to Documents
    if (request.saveToDocuments()) {
      var document =
          createLinkedDocument(templateDetail, request.entityId(), pdfResult, s3Key, memberId);
      generatedDoc.linkToDocument(document.getId());
    }

    // 7. Publish domain event
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    String actorName = resolveActorName(memberId);
    eventPublisher.publishEvent(
        new DocumentGeneratedEvent(
            "document.generated",
            "generated_document",
            generatedDoc.getId(),
            resolveProjectId(templateDetail, request.entityId()),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("file_name", pdfResult.fileName(), "template_name", templateDetail.name()),
            templateDetail.name(),
            TemplateEntityType.valueOf(templateDetail.primaryEntityType()),
            request.entityId(),
            pdfResult.fileName(),
            generatedDoc.getId()));

    // 8. Audit event
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("template_name", templateDetail.name());
    auditDetails.put("primary_entity_type", templateDetail.primaryEntityType());
    auditDetails.put("primary_entity_id", request.entityId().toString());
    auditDetails.put("file_name", pdfResult.fileName());
    auditDetails.put("file_size", pdfResult.pdfBytes().length);
    auditDetails.put("save_to_documents", request.saveToDocuments());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.generated")
            .entityType("generated_document")
            .entityId(generatedDoc.getId())
            .details(auditDetails)
            .build());

    // 9. Return response
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

  private Map<String, Object> buildContextSnapshot(TemplateDetailResponse template, UUID entityId) {
    var snapshot = new HashMap<String, Object>();
    snapshot.put("template_name", template.name());
    snapshot.put("entity_type", template.primaryEntityType());
    snapshot.put("entity_id", entityId.toString());
    return snapshot;
  }

  private Document createLinkedDocument(
      TemplateDetailResponse template,
      UUID entityId,
      PdfResult pdfResult,
      String s3Key,
      UUID memberId) {
    var entityType = TemplateEntityType.valueOf(template.primaryEntityType());
    var document =
        switch (entityType) {
          case PROJECT ->
              new Document(
                  entityId,
                  pdfResult.fileName(),
                  "application/pdf",
                  pdfResult.pdfBytes().length,
                  memberId);
          case CUSTOMER ->
              new Document(
                  Document.Scope.CUSTOMER,
                  null,
                  entityId,
                  pdfResult.fileName(),
                  "application/pdf",
                  pdfResult.pdfBytes().length,
                  memberId,
                  Document.Visibility.INTERNAL);
          case INVOICE ->
              new Document(
                  Document.Scope.ORG,
                  null,
                  null,
                  pdfResult.fileName(),
                  "application/pdf",
                  pdfResult.pdfBytes().length,
                  memberId,
                  Document.Visibility.INTERNAL);
        };
    document.assignS3Key(s3Key);
    document.confirmUpload();
    return documentRepository.save(document);
  }

  private UUID resolveProjectId(TemplateDetailResponse template, UUID entityId) {
    var entityType = TemplateEntityType.valueOf(template.primaryEntityType());
    return switch (entityType) {
      case PROJECT -> entityId;
      case CUSTOMER, INVOICE -> null;
    };
  }

  private String resolveActorName(UUID memberId) {
    return memberRepository.findOneById(memberId).map(m -> m.getName()).orElse("Unknown");
  }

  // --- DTOs ---

  public record PreviewRequest(@NotNull UUID entityId) {}

  public record GenerateDocumentRequest(@NotNull UUID entityId, boolean saveToDocuments) {}

  public record GenerateDocumentResponse(
      UUID id, String fileName, long fileSize, UUID documentId, Instant generatedAt) {}

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
