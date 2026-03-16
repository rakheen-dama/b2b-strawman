package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseResolver;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.DocumentGeneratedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.PrerequisiteNotMetException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.exception.ValidationWarningException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.ClauseSelection;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateDetailResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GeneratedDocumentService {

  private static final Logger log = LoggerFactory.getLogger(GeneratedDocumentService.class);

  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);

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
  private final DocxMergeService docxMergeService;
  private final PdfConversionService pdfConversionService;
  private final ProjectAccessService projectAccessService;
  private final List<TemplateContextBuilder> contextBuilders;
  private final OrgSettingsRepository orgSettingsRepository;

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
      PrerequisiteService prerequisiteService,
      DocxMergeService docxMergeService,
      PdfConversionService pdfConversionService,
      ProjectAccessService projectAccessService,
      List<TemplateContextBuilder> contextBuilders,
      OrgSettingsRepository orgSettingsRepository) {
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
    this.docxMergeService = docxMergeService;
    this.pdfConversionService = pdfConversionService;
    this.projectAccessService = projectAccessService;
    this.contextBuilders = contextBuilders;
    this.orgSettingsRepository = orgSettingsRepository;
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
    String tenantId = RequestScopes.requireTenantId();
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

  /**
   * Resolves the default invoice template based on the org's vertical profile. If verticalProfile
   * is "accounting-za", returns the "invoice-za" pack template if it exists. Otherwise returns the
   * generic "invoice" pack template if it exists. Falls back gracefully.
   *
   * <p>Uses {@code packTemplateKey} (the stable identifier from the template pack definition)
   * rather than slug (which is auto-generated from the template name).
   */
  @Transactional(readOnly = true)
  public Optional<DocumentTemplate> resolveDefaultInvoiceTemplate() {
    String verticalProfile =
        orgSettingsRepository
            .findForCurrentTenant()
            .map(OrgSettings::getVerticalProfile)
            .orElse(null);
    if ("accounting-za".equals(verticalProfile)) {
      Optional<DocumentTemplate> preferred =
          documentTemplateRepository.findByPackIdAndPackTemplateKey("accounting-za", "invoice-za");
      if (preferred.isPresent()) {
        return preferred;
      }
    }
    // Fallback: try a generic "invoice" template from any pack
    var genericCandidates = documentTemplateRepository.findByPackTemplateKey("invoice");
    return genericCandidates.isEmpty()
        ? Optional.empty()
        : Optional.of(genericCandidates.getFirst());
  }

  /**
   * Generates a filled DOCX document by merging a DOCX template with entity context data. Downloads
   * the template from S3, resolves context via the appropriate builder, merges placeholders,
   * uploads the result, and persists a GeneratedDocument record.
   */
  @Transactional
  public GenerateDocxResult generateDocx(
      UUID templateId, UUID entityId, String rawOutputFormat, UUID memberId) {
    // 0. Resolve and validate output format
    OutputFormat requestedFormat = resolveOutputFormat(rawOutputFormat);

    // 1. Load template and verify format
    var template =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    if (template.getFormat() != TemplateFormat.DOCX) {
      throw new InvalidStateException(
          "Invalid Template Format",
          "Cannot generate DOCX from a "
              + template.getFormat()
              + " template. "
              + "Only DOCX templates are supported for DOCX generation.");
    }

    if (template.getDocxS3Key() == null) {
      throw new InvalidStateException(
          "Missing DOCX Template File",
          "The template does not have an uploaded DOCX file. "
              + "Please upload a DOCX file before generating documents.");
    }

    // 2. Download template .docx from S3
    byte[] templateBytes = storageService.download(template.getDocxS3Key());

    // 3. Build context using appropriate builder
    var builder = findBuilder(template.getPrimaryEntityType());
    var context = builder.buildContext(entityId, memberId);

    // 4. Merge template with context
    byte[] mergedBytes;
    try {
      mergedBytes = docxMergeService.merge(new ByteArrayInputStream(templateBytes), context);
    } catch (IOException e) {
      throw new DocxGenerationException("Failed to merge DOCX template", e);
    }

    // 5. Generate file names
    String entityName = extractEntityName(template.getPrimaryEntityType(), context);
    String docxFileName = buildDocxFileName(template.getSlug(), entityName, "docx");

    // 6. Upload merged DOCX to S3
    String tenantId = RequestScopes.requireTenantId();
    String docxS3Key = "org/" + tenantId + "/generated/" + docxFileName;
    try {
      storageService.upload(docxS3Key, mergedBytes, DOCX_CONTENT_TYPE);
    } catch (Exception e) {
      throw new DocxGenerationException("Failed to upload generated DOCX to storage", e);
    }

    // 7. Handle PDF conversion if requested
    boolean wantsPdf = requestedFormat == OutputFormat.PDF || requestedFormat == OutputFormat.BOTH;
    String pdfDownloadUrl = null;
    String pdfS3Key = null;
    long pdfFileSize = 0;
    var warnings = new ArrayList<String>();
    OutputFormat storedFormat = OutputFormat.DOCX;

    if (wantsPdf) {
      var pdfBytes = pdfConversionService.convertToPdf(mergedBytes);
      if (pdfBytes.isPresent()) {
        pdfFileSize = pdfBytes.get().length;
        String pdfFileName = buildDocxFileName(template.getSlug(), entityName, "pdf");
        pdfS3Key = "org/" + tenantId + "/generated/" + pdfFileName;
        try {
          storageService.upload(pdfS3Key, pdfBytes.get(), "application/pdf");
        } catch (Exception e) {
          throw new DocxGenerationException("Failed to upload generated PDF to storage", e);
        }
        var pdfPresigned = storageService.generateDownloadUrl(pdfS3Key, DOWNLOAD_URL_EXPIRY);
        pdfDownloadUrl = pdfPresigned.url();

        // For PDF-only: primary output is PDF, store DOCX as secondary
        if (requestedFormat == OutputFormat.PDF) {
          storedFormat = OutputFormat.PDF;
        }
        // For BOTH: primary output is DOCX (storedFormat stays DOCX)
      } else {
        warnings.add("PDF conversion unavailable. DOCX output returned instead.");
      }
    }

    // 8. Build context snapshot
    var contextSnapshot = new HashMap<String, Object>();
    contextSnapshot.put("template_name", template.getName());
    contextSnapshot.put("entity_type", template.getPrimaryEntityType().name());
    contextSnapshot.put("entity_id", entityId.toString());

    // 9. Create GeneratedDocument record
    // Determine s3Key and docxS3Key based on stored format
    String primaryS3Key;
    String secondaryDocxS3Key = null;
    if (storedFormat == OutputFormat.PDF && pdfS3Key != null) {
      // Primary output is PDF
      primaryS3Key = pdfS3Key;
      secondaryDocxS3Key = docxS3Key;
    } else {
      // Primary output is DOCX (including BOTH and fallback)
      primaryS3Key = docxS3Key;
    }

    String primaryFileName =
        storedFormat == OutputFormat.PDF && pdfS3Key != null
            ? buildDocxFileName(template.getSlug(), entityName, "pdf")
            : docxFileName;

    long fileSize =
        storedFormat == OutputFormat.PDF && pdfS3Key != null ? pdfFileSize : mergedBytes.length;

    var generatedDoc =
        createRecord(
            templateId,
            template.getPrimaryEntityType(),
            entityId,
            primaryFileName,
            primaryS3Key,
            fileSize,
            memberId,
            contextSnapshot,
            storedFormat);

    if (secondaryDocxS3Key != null) {
      generatedDoc.setDocxS3Key(secondaryDocxS3Key);
    }

    // 10. Audit event
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("templateId", templateId.toString());
    auditDetails.put("entityType", template.getPrimaryEntityType().name());
    auditDetails.put("entityId", entityId.toString());
    auditDetails.put("outputFormat", storedFormat.name());
    auditDetails.put("requestedFormat", requestedFormat.name());
    auditDetails.put("fileName", primaryFileName);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("docx_document.generated")
            .entityType("generated_document")
            .entityId(generatedDoc.getId())
            .details(auditDetails)
            .build());

    // 11. Generate presigned download URL for DOCX
    // Per spec: downloadUrl = DOCX, pdfDownloadUrl = PDF
    var docxPresigned = storageService.generateDownloadUrl(docxS3Key, DOWNLOAD_URL_EXPIRY);
    String downloadUrl = docxPresigned.url();

    return new GenerateDocxResult(
        generatedDoc.getId(),
        templateId,
        template.getName(),
        storedFormat.name(),
        primaryFileName,
        downloadUrl,
        pdfDownloadUrl,
        fileSize,
        generatedDoc.getGeneratedAt(),
        warnings);
  }

  /**
   * Returns a presigned download URL for the DOCX version of a generated document. Verifies project
   * access if the document is project-scoped. Throws if no DOCX version is available.
   */
  @Transactional(readOnly = true)
  public String getDocxDownloadUrl(UUID id) {
    var generatedDoc = getById(id);
    if (generatedDoc.getPrimaryEntityType() == TemplateEntityType.PROJECT) {
      var actor = ActorContext.fromRequestScopes();
      projectAccessService.requireViewAccess(generatedDoc.getPrimaryEntityId(), actor);
    }
    String docxKey = generatedDoc.getDocxS3Key();
    if (docxKey == null) {
      throw ResourceNotFoundException.withDetail(
          "DOCX Not Available", "DOCX version not available for this document");
    }
    var presigned = storageService.generateDownloadUrl(docxKey, DOWNLOAD_URL_EXPIRY);
    return presigned.url();
  }

  // --- Private helpers ---

  private OutputFormat resolveOutputFormat(String rawOutputFormat) {
    if (rawOutputFormat == null || rawOutputFormat.isBlank()) {
      return OutputFormat.DOCX;
    }
    try {
      return OutputFormat.valueOf(rawOutputFormat.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid Output Format",
          "'"
              + rawOutputFormat
              + "' is not a valid output format. "
              + "Valid values: "
              + Arrays.toString(OutputFormat.values()));
    }
  }

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
    return createRecord(
        templateId,
        entityType,
        entityId,
        fileName,
        s3Key,
        fileSize,
        generatedBy,
        contextSnapshot,
        OutputFormat.PDF);
  }

  private GeneratedDocument createRecord(
      UUID templateId,
      TemplateEntityType entityType,
      UUID entityId,
      String fileName,
      String s3Key,
      long fileSize,
      UUID generatedBy,
      Map<String, Object> contextSnapshot,
      OutputFormat outputFormat) {
    var generatedDocument =
        new GeneratedDocument(
            templateId, entityType, entityId, fileName, s3Key, fileSize, generatedBy);
    generatedDocument.setContextSnapshot(contextSnapshot);
    generatedDocument.setOutputFormat(outputFormat);
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
        gd.getDocumentId(),
        gd.getOutputFormat().name(),
        gd.getDocxS3Key() != null);
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
      UUID documentId,
      String outputFormat,
      boolean hasDocxDownload) {}

  /** Result DTO for DOCX generation. */
  public record GenerateDocxResult(
      UUID id,
      UUID templateId,
      String templateName,
      String outputFormat,
      String fileName,
      String downloadUrl,
      String pdfDownloadUrl,
      Long fileSize,
      Instant generatedAt,
      List<String> warnings) {}

  private TemplateContextBuilder findBuilder(TemplateEntityType entityType) {
    return contextBuilders.stream()
        .filter(b -> b.supports() == entityType)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No context builder registered for entity type: " + entityType));
  }

  @SuppressWarnings("unchecked")
  private String extractEntityName(TemplateEntityType entityType, Map<String, Object> context) {
    return switch (entityType) {
      case PROJECT -> {
        var project = (Map<String, Object>) context.get("project");
        yield project != null ? (String) project.get("name") : "document";
      }
      case CUSTOMER -> {
        var customer = (Map<String, Object>) context.get("customer");
        yield customer != null ? (String) customer.get("name") : "document";
      }
      case INVOICE -> {
        var invoice = (Map<String, Object>) context.get("invoice");
        yield invoice != null && invoice.get("invoiceNumber") != null
            ? (String) invoice.get("invoiceNumber")
            : "document";
      }
    };
  }

  private String slugifyName(String text) {
    if (text == null || text.isBlank()) {
      return "document";
    }
    String slugified =
        text.toLowerCase()
            .replaceAll("[\\s]+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    if (slugified.isEmpty()) {
      return "document";
    }
    return slugified.length() > 50 ? slugified.substring(0, 50) : slugified;
  }

  private String buildDocxFileName(String templateSlug, String entityName, String extension) {
    String slugifiedName = slugifyName(entityName);
    String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    return templateSlug + "-" + slugifiedName + "-" + date + "." + extension;
  }
}
