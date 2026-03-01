package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseResolver;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.exception.PrerequisiteNotMetException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.exception.ValidationWarningException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.ClauseSelection;
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
  private final MemberNameResolver memberNameResolver;
  private final PdfRenderingService pdfRenderingService;
  private final DocumentTemplateService documentTemplateService;
  private final TemplateValidationService templateValidationService;
  private final StorageService storageService;
  private final DocumentRepository documentRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final ClauseResolver clauseResolver;
  private final PrerequisiteService prerequisiteService;

  public GeneratedDocumentService(
      GeneratedDocumentRepository generatedDocumentRepository,
      DocumentTemplateRepository documentTemplateRepository,
      MemberNameResolver memberNameResolver,
      PdfRenderingService pdfRenderingService,
      DocumentTemplateService documentTemplateService,
      TemplateValidationService templateValidationService,
      StorageService storageService,
      DocumentRepository documentRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      ClauseResolver clauseResolver,
      PrerequisiteService prerequisiteService) {
    this.generatedDocumentRepository = generatedDocumentRepository;
    this.documentTemplateRepository = documentTemplateRepository;
    this.memberNameResolver = memberNameResolver;
    this.pdfRenderingService = pdfRenderingService;
    this.documentTemplateService = documentTemplateService;
    this.templateValidationService = templateValidationService;
    this.storageService = storageService;
    this.documentRepository = documentRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.clauseResolver = clauseResolver;
    this.prerequisiteService = prerequisiteService;
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
      UUID templateId,
      UUID entityId,
      boolean saveToDocuments,
      boolean acknowledgeWarnings,
      List<ClauseSelection> clauseSelections,
      UUID memberId) {
    // 0. Check action-point prerequisites
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    UUID customerId = resolveCustomerIdForDocGen(template.getPrimaryEntityType(), entityId);
    if (customerId != null) {
      var prerequisiteCheck =
          prerequisiteService.checkForContext(
              PrerequisiteContext.DOCUMENT_GENERATION, EntityType.CUSTOMER, customerId);
      if (!prerequisiteCheck.passed()) {
        throw new PrerequisiteNotMetException(prerequisiteCheck);
      }
    }

    // 0b. Validate required fields before generation
    var contextMap = pdfRenderingService.buildContext(templateId, entityId, memberId);
    var validationResult =
        templateValidationService.validateRequiredFields(
            template.getRequiredContextFields(), contextMap);

    if (!validationResult.allPresent() && !acknowledgeWarnings) {
      throw new ValidationWarningException(validationResult);
    }

    // 0b. Resolve clauses
    var resolvedClauses = clauseResolver.resolveClauses(templateId, clauseSelections);

    // 1. Generate PDF (clause-aware)
    var pdfResult =
        pdfRenderingService.generatePdf(templateId, entityId, memberId, resolvedClauses);

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

    // 5b. Store warnings if validation had missing fields
    if (!validationResult.allPresent()) {
      var warnings =
          validationResult.fields().stream()
              .filter(f -> !f.present())
              .map(
                  f ->
                      Map.<String, Object>of(
                          "entity", f.entity(), "field", f.field(), "reason", f.reason()))
              .toList();
      generatedDoc.setWarnings(warnings);
    }

    // 5c. Store clause snapshots
    if (!resolvedClauses.isEmpty()) {
      generatedDoc.setClauseSnapshots(buildClauseSnapshots(resolvedClauses));
    }

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

    // 8b. Supplementary audit event when clauses are included
    if (!resolvedClauses.isEmpty()) {
      var clauseAuditDetails = new HashMap<String, Object>();
      clauseAuditDetails.put("template_name", templateDetail.name());
      clauseAuditDetails.put(
          "clause_slugs", resolvedClauses.stream().map(Clause::getSlug).toList());
      clauseAuditDetails.put("clause_count", resolvedClauses.size());
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("document.generated_with_clauses")
              .entityType("generated_document")
              .entityId(generatedDoc.getId())
              .details(clauseAuditDetails)
              .build());
    }

    return new GenerationResult(generatedDoc, pdfResult);
  }

  /**
   * Previews a document with optional clause resolution. Resolves clauses via ClauseResolver before
   * delegating to PdfRenderingService.
   */
  @Transactional(readOnly = true)
  public PdfRenderingService.PreviewResponse previewDocument(
      UUID templateId, UUID entityId, List<ClauseSelection> clauseSelections, UUID memberId) {
    var resolvedClauses = clauseResolver.resolveClauses(templateId, clauseSelections);
    return pdfRenderingService.previewWithValidation(
        templateId, entityId, memberId, resolvedClauses);
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

  private List<Map<String, Object>> buildClauseSnapshots(List<Clause> resolvedClauses) {
    var snapshots = new java.util.ArrayList<Map<String, Object>>();
    for (int i = 0; i < resolvedClauses.size(); i++) {
      var clause = resolvedClauses.get(i);
      var snapshot = new HashMap<String, Object>();
      snapshot.put("clauseId", clause.getId().toString());
      snapshot.put("slug", clause.getSlug());
      snapshot.put("title", clause.getTitle());
      snapshot.put("body", clause.getBody());
      snapshot.put("sortOrder", i);
      snapshots.add(snapshot);
    }
    return snapshots;
  }

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

  private UUID resolveCustomerIdForDocGen(TemplateEntityType entityType, UUID entityId) {
    return switch (entityType) {
      case CUSTOMER -> entityId;
      case PROJECT -> {
        try {
          yield prerequisiteService.resolveCustomerIdFromProject(entityId);
        } catch (ResourceNotFoundException e) {
          log.warn("No customer linked to project {} — skipping prerequisite check", entityId);
          yield null;
        }
      }
      case INVOICE -> null; // Invoice prerequisite checks not yet supported
    };
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
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
    return memberNameResolver.resolveName(memberId);
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
