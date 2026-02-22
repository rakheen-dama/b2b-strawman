package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateDetailResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneratedDocumentService {

  private static final Logger log = LoggerFactory.getLogger(GeneratedDocumentService.class);

  private final GeneratedDocumentRepository generatedDocumentRepository;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final MemberRepository memberRepository;
  private final PdfRenderingService pdfRenderingService;
  private final DocumentTemplateService documentTemplateService;
  private final StorageService storageService;
  private final DocumentRepository documentRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public GeneratedDocumentService(
      GeneratedDocumentRepository generatedDocumentRepository,
      DocumentTemplateRepository documentTemplateRepository,
      MemberRepository memberRepository,
      PdfRenderingService pdfRenderingService,
      DocumentTemplateService documentTemplateService,
      StorageService storageService,
      DocumentRepository documentRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.memberRepository = memberRepository;
    this.pdfRenderingService = pdfRenderingService;
    this.documentTemplateService = documentTemplateService;
    this.storageService = storageService;
    this.documentRepository = documentRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Orchestrates the full document generation pipeline: render PDF, upload to S3, persist
   * GeneratedDocument, optionally create a linked Document, publish domain event, and audit.
   *
   * <p>The entire flow runs within a single transaction so that the GeneratedDocument entity stays
   * managed and the {@code linkToDocument()} call is persisted automatically.
   */
  @Transactional
  public GenerationResult generateDocument(
      UUID templateId, UUID entityId, boolean saveToDocuments, UUID memberId) {
    // 1. Generate PDF
    var pdfResult = pdfRenderingService.generatePdf(templateId, entityId, memberId);

    // 2. Load template metadata
    var templateDetail = documentTemplateService.getById(templateId);

    // 3. Upload to storage
    String tenantId = RequestScopes.TENANT_ID.get();
    String s3Key = "org/" + tenantId + "/generated/" + pdfResult.fileName();
    uploadToStorage(s3Key, pdfResult.pdfBytes());

    // 4. Build context snapshot
    var contextSnapshot = buildContextSnapshot(templateDetail, entityId);

    // 5. Create GeneratedDocument record (managed — stays attached in this TX)
    var generatedDoc =
        createRecord(
            templateId,
            TemplateEntityType.valueOf(templateDetail.primaryEntityType()),
            entityId,
            pdfResult.fileName(),
            s3Key,
            pdfResult.pdfBytes().length,
            memberId,
            contextSnapshot);

    // 6. Optionally save to Documents and link
    if (saveToDocuments) {
      var document = createLinkedDocument(templateDetail, entityId, pdfResult, s3Key, memberId);
      generatedDoc.linkToDocument(document.getId());
      // No explicit save needed — entity is managed within this transaction
    }

    // 7. Publish domain event
    String orgId = RequestScopes.requireOrgId();
    String actorName = resolveActorName(memberId);
    eventPublisher.publishEvent(
        new DocumentGeneratedEvent(
            "document.generated",
            "generated_document",
            generatedDoc.getId(),
            resolveProjectId(templateDetail, entityId),
            memberId,
            actorName,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("file_name", pdfResult.fileName(), "template_name", templateDetail.name()),
            templateDetail.name(),
            TemplateEntityType.valueOf(templateDetail.primaryEntityType()),
            entityId,
            pdfResult.fileName(),
            generatedDoc.getId()));

    // 8. Audit event
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("template_name", templateDetail.name());
    auditDetails.put("primary_entity_type", templateDetail.primaryEntityType());
    auditDetails.put("primary_entity_id", entityId.toString());
    auditDetails.put("file_name", pdfResult.fileName());
    auditDetails.put("file_size", pdfResult.pdfBytes().length);
    auditDetails.put("save_to_documents", saveToDocuments);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.generated")
            .entityType("generated_document")
            .entityId(generatedDoc.getId())
            .details(auditDetails)
            .build());

    return new GenerationResult(generatedDoc, pdfResult);
  }

  @Transactional(readOnly = true)
  public List<GeneratedDocumentListResponse> listByEntity(
      TemplateEntityType entityType, UUID entityId) {
    var documents =
        generatedDocumentRepository.findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
            entityType, entityId);
    return documents.stream().map(this::toListResponse).toList();
  }

  @Transactional(readOnly = true)
  public GeneratedDocument getById(UUID id) {
    return generatedDocumentRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", id));
  }

  @Transactional
  public void delete(UUID id) {
    var doc =
        generatedDocumentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GeneratedDocument", id));
    generatedDocumentRepository.delete(doc);
    log.info("Deleted generated document: id={}", id);
  }

  // --- Private helpers ---

  private GeneratedDocument createRecord(
      UUID templateId,
      TemplateEntityType entityType,
      UUID entityId,
      String fileName,
      String s3Key,
      long fileSize,
      UUID generatedBy,
      Map<String, Object> contextSnapshot) {
    var generatedDocument =
        new GeneratedDocument(
            templateId, entityType, entityId, fileName, s3Key, fileSize, generatedBy);
    generatedDocument.setContextSnapshot(contextSnapshot);
    generatedDocument = generatedDocumentRepository.save(generatedDocument);
    log.info(
        "Created generated document: id={}, template={}, entity={}/{}",
        generatedDocument.getId(),
        templateId,
        entityType,
        entityId);
    return generatedDocument;
  }

  private void uploadToStorage(String s3Key, byte[] pdfBytes) {
    try {
      storageService.upload(s3Key, pdfBytes, "application/pdf");
    } catch (Exception e) {
      throw new PdfGenerationException("Failed to upload generated PDF to storage", e);
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
    return memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
  }

  private GeneratedDocumentListResponse toListResponse(GeneratedDocument gd) {
    String templateName = resolveTemplateName(gd.getTemplateId());
    String generatedByName = resolveGeneratedByName(gd.getGeneratedBy());
    return new GeneratedDocumentListResponse(
        gd.getId(),
        templateName,
        gd.getPrimaryEntityType(),
        gd.getPrimaryEntityId(),
        gd.getFileName(),
        gd.getFileSize(),
        generatedByName,
        gd.getGeneratedAt(),
        gd.getDocumentId());
  }

  private String resolveTemplateName(UUID templateId) {
    return documentTemplateRepository
        .findById(templateId)
        .map(DocumentTemplate::getName)
        .orElse("Deleted Template");
  }

  private String resolveGeneratedByName(UUID memberId) {
    return memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
  }

  /**
   * Result of a document generation operation, containing both the persisted entity and the PDF.
   */
  public record GenerationResult(GeneratedDocument generatedDocument, PdfResult pdfResult) {}

  /** DTO for generated document list responses. */
  public record GeneratedDocumentListResponse(
      UUID id,
      String templateName,
      TemplateEntityType primaryEntityType,
      UUID primaryEntityId,
      String fileName,
      long fileSize,
      String generatedByName,
      Instant generatedAt,
      UUID documentId) {}
}
