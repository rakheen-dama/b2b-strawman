package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.clause.TemplateClauseSync;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateDetailResponse;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateListResponse;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.UpdateTemplateRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentTemplateService {

  private static final Logger log = LoggerFactory.getLogger(DocumentTemplateService.class);
  private static final Set<String> VALID_ENTITY_KEYS =
      Set.of("customer", "project", "invoice", "task", "org");

  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final long MAX_DOCX_SIZE = 10 * 1024 * 1024; // 10MB
  private static final Duration URL_EXPIRY = Duration.ofHours(1);

  private final DocumentTemplateRepository documentTemplateRepository;
  private final ClauseRepository clauseRepository;
  private final AuditService auditService;
  private final TemplateClauseSync templateClauseSync;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final TemplateVariableAnalyzer templateVariableAnalyzer;
  private final StorageService storageService;
  private final DocxMergeService docxMergeService;
  private final DocxFieldValidator docxFieldValidator;

  public DocumentTemplateService(
      DocumentTemplateRepository documentTemplateRepository,
      ClauseRepository clauseRepository,
      AuditService auditService,
      TemplateClauseSync templateClauseSync,
      FieldDefinitionRepository fieldDefinitionRepository,
      TemplateVariableAnalyzer templateVariableAnalyzer,
      StorageService storageService,
      DocxMergeService docxMergeService,
      DocxFieldValidator docxFieldValidator) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.clauseRepository = clauseRepository;
    this.auditService = auditService;
    this.templateClauseSync = templateClauseSync;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.templateVariableAnalyzer = templateVariableAnalyzer;
    this.storageService = storageService;
    this.docxMergeService = docxMergeService;
    this.docxFieldValidator = docxFieldValidator;
  }

  /**
   * Lists templates with optional filters. Filter priority: category > primaryEntityType > format.
   * At most one filter is applied; if none are provided, all active templates are returned.
   */
  @Transactional(readOnly = true)
  public List<TemplateListResponse> listTemplates(
      TemplateCategory category, TemplateEntityType primaryEntityType, TemplateFormat format) {
    List<DocumentTemplate> templates;
    if (category != null) {
      templates = documentTemplateRepository.findByCategoryAndActiveTrueOrderBySortOrder(category);
    } else if (primaryEntityType != null) {
      templates =
          documentTemplateRepository.findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(
              primaryEntityType);
    } else if (format != null) {
      templates = documentTemplateRepository.findByFormatAndActiveTrueOrderBySortOrder(format);
    } else {
      templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
    }
    return templates.stream().map(TemplateListResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public TemplateDetailResponse getById(UUID id) {
    var dt =
        documentTemplateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));
    var response = TemplateDetailResponse.from(dt);
    // Extract clauseIds first to avoid unnecessary deep copy when no clauses are present
    Set<UUID> clauseIds = new HashSet<>();
    extractClauseIds(response.content(), clauseIds);
    if (!clauseIds.isEmpty()) {
      response = response.withContent(enrichClauseTitles(response.content()));
    }
    return response;
  }

  @Transactional
  public TemplateDetailResponse create(CreateTemplateRequest request) {
    String baseSlug =
        request.slug() != null && !request.slug().isBlank()
            ? request.slug()
            : DocumentTemplate.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(baseSlug);

    TemplateCategory category = request.category();
    TemplateEntityType entityType = request.primaryEntityType();

    validateTiptapContent(request.content());
    var dt =
        new DocumentTemplate(entityType, request.name(), finalSlug, category, request.content());
    dt.setDescription(request.description());
    dt.setCss(request.css());
    validateFormatConsistency(dt);
    if (request.requiredContextFields() != null) {
      validateRequiredContextFieldEntries(request.requiredContextFields());
      dt.setRequiredContextFields(request.requiredContextFields());
    }

    try {
      dt = documentTemplateRepository.save(dt);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A document template with slug '" + finalSlug + "' already exists");
    }

    log.info(
        "Created document template: id={}, category={}, slug={}",
        dt.getId(),
        dt.getCategory(),
        dt.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.created")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(
                Map.of(
                    "category", dt.getCategory().name(),
                    "name", dt.getName(),
                    "slug", dt.getSlug(),
                    "primary_entity_type", dt.getPrimaryEntityType().name()))
            .build());

    return TemplateDetailResponse.from(dt);
  }

  /**
   * Uploads a .docx template file with multipart validation, S3 storage, and automatic field
   * discovery.
   */
  @Transactional
  public TemplateDetailResponse uploadDocxTemplate(
      MultipartFile file, String name, String description, String category, String entityType) {
    // Validate MIME type
    String contentType = file.getContentType();
    if (!DOCX_CONTENT_TYPE.equals(contentType)) {
      throw new InvalidStateException(
          "Invalid file type",
          "Expected MIME type '" + DOCX_CONTENT_TYPE + "' but got '" + contentType + "'");
    }

    // Validate file size
    if (file.getSize() > MAX_DOCX_SIZE) {
      throw new InvalidStateException(
          "File too large",
          "Maximum file size is 10MB, but the uploaded file is "
              + (file.getSize() / (1024 * 1024))
              + "MB");
    }

    // Validate name
    if (name == null || name.isBlank()) {
      throw new InvalidStateException("Invalid name", "Template name must not be blank");
    }

    // Parse enums
    TemplateCategory templateCategory;
    try {
      templateCategory = TemplateCategory.valueOf(category);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidStateException("Invalid category", "Invalid category value: " + category);
    }

    TemplateEntityType templateEntityType;
    try {
      templateEntityType = TemplateEntityType.valueOf(entityType);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidStateException(
          "Invalid entity type", "Invalid entity type value: " + entityType);
    }

    // Generate unique slug
    String baseSlug = DocumentTemplate.generateSlug(name);
    String finalSlug = resolveUniqueSlug(baseSlug);

    // Sanitize filename — strip directory components and reject null/blank
    String originalFilename = file.getOriginalFilename();
    String safeFilename = null;
    if (originalFilename != null && !originalFilename.isBlank()) {
      safeFilename = Path.of(originalFilename).getFileName().toString();
    }
    if (safeFilename == null || safeFilename.isBlank()) {
      safeFilename = "template.docx";
    }

    // Read file bytes early — needed for both field discovery and S3 upload
    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      throw new InvalidStateException("Upload failed", "Could not read the uploaded file");
    }

    // Discover and validate fields BEFORE S3 upload — rejects corrupt/malicious files early
    List<String> fieldPaths;
    try {
      fieldPaths = docxMergeService.discoverFields(new ByteArrayInputStream(fileBytes));
    } catch (IOException | POIXMLException | UnsupportedFileFormatException e) {
      throw new InvalidStateException(
          "Corrupt file", "The uploaded file could not be parsed as a valid .docx document");
    }

    var validationResults = docxFieldValidator.validateFields(fieldPaths, templateEntityType);
    List<Map<String, Object>> discoveredFields =
        validationResults.stream()
            .map(
                r -> {
                  Map<String, Object> field = new HashMap<>();
                  field.put("path", r.path());
                  field.put("status", r.status());
                  if (r.label() != null) {
                    field.put("label", r.label());
                  }
                  return field;
                })
            .toList();

    // Create entity with DOCX format
    var dt = new DocumentTemplate(templateEntityType, name, finalSlug, templateCategory, null);
    dt.setFormat(TemplateFormat.DOCX);
    dt.setDescription(description);
    dt.setDocxFileName(safeFilename);
    dt.setDocxFileSize(file.getSize());
    dt.setDiscoveredFields(discoveredFields);

    try {
      dt = documentTemplateRepository.save(dt);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A document template with slug '" + finalSlug + "' already exists");
    }

    // Upload to S3 LAST — after all validation and DB save, to avoid orphaned S3 objects
    String tenantId = RequestScopes.requireTenantId();
    String s3Key = "org/" + tenantId + "/templates/" + dt.getId() + "/template.docx";
    storageService.upload(s3Key, fileBytes, DOCX_CONTENT_TYPE);
    dt.setDocxS3Key(s3Key);

    dt = documentTemplateRepository.save(dt);

    log.info(
        "Uploaded DOCX template: id={}, slug={}, fields={}",
        dt.getId(),
        dt.getSlug(),
        fieldPaths.size());

    // Audit event
    long unknownCount =
        validationResults.stream().filter(r -> "UNKNOWN".equals(r.status())).count();
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("templateId", dt.getId().toString());
    auditDetails.put("fileName", dt.getDocxFileName());
    auditDetails.put("fileSize", dt.getDocxFileSize());
    auditDetails.put("fieldCount", fieldPaths.size());
    auditDetails.put("unknownFieldCount", unknownCount);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("docx_template.uploaded")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(auditDetails)
            .build());

    return TemplateDetailResponse.from(dt);
  }

  @Transactional
  public TemplateDetailResponse update(UUID id, UpdateTemplateRequest request) {
    var dt =
        documentTemplateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));

    validateTiptapContent(request.content());

    // Track changed fields for audit
    var changedFields = new ArrayList<String>();
    if (request.name() != null && !request.name().equals(dt.getName())) {
      changedFields.add("name");
    }
    if (request.description() != null && !request.description().equals(dt.getDescription())) {
      changedFields.add("description");
    }
    if (request.content() != null && !request.content().equals(dt.getContent())) {
      changedFields.add("content");
    }
    if (request.css() != null && !java.util.Objects.equals(request.css(), dt.getCss())) {
      changedFields.add("css");
    }
    if (request.sortOrder() != null
        && !java.util.Objects.equals(request.sortOrder(), dt.getSortOrder())) {
      changedFields.add("sortOrder");
    }
    if (request.requiredContextFields() != null
        && !java.util.Objects.equals(
            request.requiredContextFields(), dt.getRequiredContextFields())) {
      changedFields.add("requiredContextFields");
    }

    dt.updateContent(
        request.name() != null ? request.name() : dt.getName(),
        request.description() != null ? request.description() : dt.getDescription(),
        request.content() != null ? request.content() : dt.getContent(),
        request.css() != null ? request.css() : dt.getCss());

    if (request.sortOrder() != null) {
      dt.setSortOrder(request.sortOrder());
    }

    // requiredContextFields: allow null to clear, non-null to set (with validation)
    if (request.requiredContextFields() != null) {
      validateRequiredContextFieldEntries(request.requiredContextFields());
    }
    dt.setRequiredContextFields(request.requiredContextFields());

    // Validate format consistency AFTER all fields are applied but BEFORE save
    validateFormatConsistency(dt);

    dt = documentTemplateRepository.save(dt);

    // Sync clause associations from document JSON only when content changed (ADR-123)
    if (request.content() != null) {
      templateClauseSync.syncClausesFromDocument(dt.getId(), dt.getContent());
    }

    log.info("Updated document template: id={}, name={}", dt.getId(), dt.getName());

    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("name", dt.getName());
    auditDetails.put("changed_fields", changedFields);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.updated")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(auditDetails)
            .build());

    return TemplateDetailResponse.from(dt);
  }

  @Transactional
  public void deactivate(UUID id) {
    var dt =
        documentTemplateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));

    // Clean up DOCX S3 file if present — best-effort, template deactivation must succeed
    if (dt.getFormat() == TemplateFormat.DOCX && dt.getDocxS3Key() != null) {
      try {
        storageService.delete(dt.getDocxS3Key());
        log.info("Deleted DOCX S3 file: key={}", dt.getDocxS3Key());
      } catch (Exception e) {
        log.warn(
            "Failed to delete DOCX S3 file: key={}, error={}", dt.getDocxS3Key(), e.getMessage());
      }
    }

    dt.deactivate();
    documentTemplateRepository.save(dt);

    log.info("Deactivated document template: id={}, slug={}", dt.getId(), dt.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.deleted")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(
                Map.of(
                    "name", dt.getName(),
                    "category", dt.getCategory().name(),
                    "slug", dt.getSlug()))
            .build());
  }

  /**
   * Clones a PLATFORM template as an ORG_CUSTOM copy. The clone shares the same packId and
   * packTemplateKey but has source=ORG_CUSTOM and sourceTemplateId pointing to the original.
   *
   * @throws ResourceNotFoundException if the template does not exist
   * @throws InvalidStateException if the template is not a PLATFORM template
   * @throws ResourceConflictException if an ORG_CUSTOM clone already exists for the same
   *     pack_template_key
   */
  @Transactional
  public TemplateDetailResponse cloneTemplate(UUID templateId) {
    var original =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    if (original.getSource() != TemplateSource.PLATFORM) {
      throw new InvalidStateException(
          "Invalid clone source", "Only PLATFORM templates can be cloned");
    }

    // Check for existing clone with same pack_template_key
    if (original.getPackTemplateKey() != null) {
      var existing =
          documentTemplateRepository.findByPackTemplateKey(original.getPackTemplateKey()).stream()
              .filter(t -> t.getSource() == TemplateSource.ORG_CUSTOM)
              .findFirst();
      if (existing.isPresent()) {
        throw new ResourceConflictException(
            "Clone exists", "An ORG_CUSTOM clone of this template already exists");
      }
    }

    // Create clone
    String cloneSlug = resolveUniqueSlug(original.getSlug() + "-custom");
    var clone =
        new DocumentTemplate(
            original.getPrimaryEntityType(),
            original.getName() + " (Custom)",
            cloneSlug,
            original.getCategory(),
            original.getContent());
    clone.setDescription(original.getDescription());
    clone.setCss(original.getCss());
    clone.setSourceTemplateId(original.getId());
    clone.setPackId(original.getPackId());
    clone.setPackTemplateKey(original.getPackTemplateKey());
    clone.setSortOrder(original.getSortOrder());
    // source defaults to ORG_CUSTOM from constructor

    clone = documentTemplateRepository.save(clone);

    var cloneDetails = new HashMap<String, Object>();
    cloneDetails.put("original_id", original.getId().toString());
    cloneDetails.put("original_name", original.getName());
    cloneDetails.put("clone_name", clone.getName());
    if (original.getPackId() != null) {
      cloneDetails.put("pack_id", original.getPackId());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.cloned")
            .entityType("document_template")
            .entityId(clone.getId())
            .details(cloneDetails)
            .build());

    log.info("Cloned template: original={}, clone={}", original.getId(), clone.getId());
    return TemplateDetailResponse.from(clone);
  }

  /**
   * Resets an ORG_CUSTOM clone by hard-deleting it, leaving only the original PLATFORM template.
   *
   * @throws ResourceNotFoundException if the template does not exist
   * @throws InvalidStateException if the template is not ORG_CUSTOM or has no sourceTemplateId
   */
  @Transactional
  public void resetToDefault(UUID cloneId) {
    var clone =
        documentTemplateRepository
            .findById(cloneId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", cloneId));

    if (clone.getSource() != TemplateSource.ORG_CUSTOM) {
      throw new InvalidStateException(
          "Invalid reset target", "Only ORG_CUSTOM templates can be reset");
    }

    if (clone.getSourceTemplateId() == null) {
      throw new InvalidStateException(
          "Not a clone", "This template was not cloned from a PLATFORM template");
    }

    documentTemplateRepository.delete(clone);

    log.info("Reset template: deleted clone {}", cloneId);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.reset")
            .entityType("document_template")
            .entityId(cloneId)
            .details(Map.of("source_template_id", clone.getSourceTemplateId().toString()))
            .build());
  }

  /**
   * Computes which field packs a template requires based on its custom field variable references,
   * and whether each pack is applied in the current org (i.e., matching FieldDefinitions exist).
   */
  @Transactional(readOnly = true)
  public List<FieldPackStatus> getRequiredFieldPacks(UUID templateId) {
    var dt =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    Map<String, Set<String>> entitySlugs =
        templateVariableAnalyzer.extractCustomFieldSlugs(dt.getContent());
    if (entitySlugs.isEmpty()) {
      return List.of();
    }

    // Collect all referenced slugs with their resolved FieldDefinitions
    // Key: packId, Value: { packName, set of referenced slugs, set of found slugs }
    Map<String, PackAccumulator> packMap = new LinkedHashMap<>();

    for (var entry : entitySlugs.entrySet()) {
      String entityPrefix = entry.getKey();
      Set<String> slugs = entry.getValue();

      EntityType entityType;
      try {
        entityType = EntityType.valueOf(entityPrefix.toUpperCase());
      } catch (IllegalArgumentException e) {
        // Unknown entity prefix — skip
        continue;
      }

      for (String slug : slugs) {
        var fdOpt = fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, slug);
        if (fdOpt.isPresent()) {
          FieldDefinition fd = fdOpt.get();
          String packId = fd.getPackId();
          if (packId != null) {
            packMap.computeIfAbsent(packId, k -> new PackAccumulator(packId));
          }
        } else {
          // Field not found — try to identify which pack it might belong to by convention
          // (packId matches the entity prefix pattern). We mark it as missing in an
          // "unknown" pack keyed by entityPrefix.
          String syntheticPackId = "common-" + entityPrefix;
          packMap.computeIfAbsent(syntheticPackId, k -> new PackAccumulator(syntheticPackId));
          packMap.get(syntheticPackId).addMissing(entityPrefix + ".customFields." + slug);
        }
      }
    }

    return packMap.values().stream()
        .map(
            acc -> {
              boolean applied = fieldDefinitionRepository.existsByPackIdAndActiveTrue(acc.packId);
              return new FieldPackStatus(
                  acc.packId, acc.packId, applied, List.copyOf(acc.missingFields));
            })
        .toList();
  }

  /** Mutable accumulator used during field pack status computation. */
  private static class PackAccumulator {
    final String packId;
    final List<String> missingFields = new ArrayList<>();

    PackAccumulator(String packId) {
      this.packId = packId;
    }

    void addMissing(String variableKey) {
      missingFields.add(variableKey);
    }
  }

  /** Returns the discovered merge fields for a DOCX template. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getDocxFields(UUID templateId) {
    var dt =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    if (dt.getFormat() != TemplateFormat.DOCX) {
      throw new InvalidStateException(
          "Not a DOCX template", "Only DOCX templates have discoverable fields");
    }

    var fields = dt.getDiscoveredFields();
    return fields != null ? fields : List.of();
  }

  /** Generates a presigned download URL for a DOCX template's S3 file. */
  @Transactional(readOnly = true)
  public String getDocxDownloadUrl(UUID templateId) {
    var dt =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    if (dt.getFormat() != TemplateFormat.DOCX) {
      throw new InvalidStateException(
          "Not a DOCX template", "Only DOCX templates can be downloaded");
    }

    if (dt.getDocxS3Key() == null) {
      throw new InvalidStateException(
          "No file uploaded", "This DOCX template does not have an uploaded file");
    }

    var presigned = storageService.generateDownloadUrl(dt.getDocxS3Key(), URL_EXPIRY);
    return presigned.url();
  }

  /** Replaces the DOCX file for an existing template, re-discovering and validating fields. */
  @Transactional
  public TemplateDetailResponse replaceDocxFile(UUID templateId, MultipartFile file) {
    var dt =
        documentTemplateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", templateId));

    if (dt.getFormat() != TemplateFormat.DOCX) {
      throw new InvalidStateException(
          "Not a DOCX template", "Only DOCX templates can have their file replaced");
    }

    // Validate MIME type
    String contentType = file.getContentType();
    if (!DOCX_CONTENT_TYPE.equals(contentType)) {
      throw new InvalidStateException(
          "Invalid file type",
          "Expected MIME type '" + DOCX_CONTENT_TYPE + "' but got '" + contentType + "'");
    }

    // Validate file size
    if (file.getSize() > MAX_DOCX_SIZE) {
      throw new InvalidStateException(
          "File too large",
          "Maximum file size is 10MB, but the uploaded file is "
              + (file.getSize() / (1024 * 1024))
              + "MB");
    }

    // Sanitize filename
    String originalFilename = file.getOriginalFilename();
    String safeFilename = null;
    if (originalFilename != null && !originalFilename.isBlank()) {
      safeFilename = Path.of(originalFilename).getFileName().toString();
    }
    if (safeFilename == null || safeFilename.isBlank()) {
      safeFilename = "template.docx";
    }

    // Read file bytes
    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      throw new InvalidStateException("Upload failed", "Could not read the uploaded file");
    }

    // Discover and validate fields
    List<String> fieldPaths;
    try {
      fieldPaths = docxMergeService.discoverFields(new ByteArrayInputStream(fileBytes));
    } catch (IOException | POIXMLException | UnsupportedFileFormatException e) {
      throw new InvalidStateException(
          "Corrupt file", "The uploaded file could not be parsed as a valid .docx document");
    }

    var validationResults =
        docxFieldValidator.validateFields(fieldPaths, dt.getPrimaryEntityType());
    List<Map<String, Object>> discoveredFields =
        validationResults.stream()
            .map(
                r -> {
                  Map<String, Object> field = new HashMap<>();
                  field.put("path", r.path());
                  field.put("status", r.status());
                  if (r.label() != null) {
                    field.put("label", r.label());
                  }
                  return field;
                })
            .toList();

    // Update entity metadata and save BEFORE S3 upload — if DB save fails, old S3 file is
    // preserved.
    // If S3 upload fails after DB commit, metadata is slightly ahead but the old file is intact,
    // which is recoverable. The reverse (S3 overwrite before DB save) risks permanent file loss.
    dt.setDocxFileName(safeFilename);
    dt.setDocxFileSize(file.getSize());
    dt.setDiscoveredFields(discoveredFields);
    dt.touchUpdatedAt();

    dt = documentTemplateRepository.save(dt);

    // Overwrite existing S3 object with same key AFTER DB save
    storageService.upload(dt.getDocxS3Key(), fileBytes, DOCX_CONTENT_TYPE);

    log.info(
        "Replaced DOCX template file: id={}, slug={}, fields={}",
        dt.getId(),
        dt.getSlug(),
        fieldPaths.size());

    // Audit event
    long unknownCount =
        validationResults.stream().filter(r -> "UNKNOWN".equals(r.status())).count();
    var auditDetails = new HashMap<String, Object>();
    auditDetails.put("templateId", dt.getId().toString());
    auditDetails.put("fileName", dt.getDocxFileName());
    auditDetails.put("fileSize", dt.getDocxFileSize());
    auditDetails.put("fieldCount", fieldPaths.size());
    auditDetails.put("unknownFieldCount", unknownCount);
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("docx_template.replaced")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(auditDetails)
            .build());

    return TemplateDetailResponse.from(dt);
  }

  private void validateFormatConsistency(DocumentTemplate dt) {
    if (dt.getFormat() == TemplateFormat.TIPTAP) {
      if (dt.getDocxS3Key() != null) {
        throw new InvalidStateException(
            "Invalid format consistency", "TIPTAP templates must not have a DOCX S3 key set");
      }
      if (dt.getDocxFileName() != null) {
        throw new InvalidStateException(
            "Invalid format consistency", "TIPTAP templates must not have a DOCX file name set");
      }
    }
    // DOCX templates: content may be null, docxS3Key may be null pre-upload
  }

  private void validateRequiredContextFieldEntries(List<Map<String, String>> entries) {
    for (var entry : entries) {
      String entity = entry.get("entity");
      if (entity == null || !VALID_ENTITY_KEYS.contains(entity)) {
        throw new InvalidStateException(
            "Invalid required context field",
            "Invalid entity value '" + entity + "'. Must be one of: " + VALID_ENTITY_KEYS);
      }
      String field = entry.get("field");
      if (field == null || field.isBlank()) {
        throw new InvalidStateException(
            "Invalid required context field", "Field name must not be blank");
      }
    }
  }

  /**
   * Best-effort slug uniqueness resolution. There is an inherent TOCTOU race between the
   * findBySlug() check and the subsequent INSERT — concurrent requests creating templates with the
   * same name may both pass this check. The DB unique constraint on slug is the authoritative
   * guard; a DataIntegrityViolationException from save() is caught in create() and surfaced as a
   * 409 Conflict, which is acceptable behavior for concurrent same-name creation.
   */
  private void validateTiptapContent(Map<String, Object> content) {
    if (content == null) {
      throw new InvalidStateException("Invalid content", "Content must not be null");
    }
    Object type = content.get("type");
    if (!"doc".equals(type)) {
      throw new InvalidStateException(
          "Invalid content", "Content root node must have type 'doc', got: " + type);
    }
  }

  private String resolveUniqueSlug(String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (documentTemplateRepository.findBySlug(finalSlug).isPresent()) {
      finalSlug = baseSlug + "-" + suffix;
      suffix++;
    }
    return finalSlug;
  }

  /**
   * Enriches clauseBlock nodes in the content tree with current clause titles from the library.
   * Returns a deep copy with updated titles, or the original map if no clauseBlocks are found.
   */
  @SuppressWarnings("unchecked")
  Map<String, Object> enrichClauseTitles(Map<String, Object> content) {
    if (content == null) {
      return null;
    }

    // 1. Extract all clauseIds from the content tree
    Set<UUID> clauseIds = new HashSet<>();
    extractClauseIds(content, clauseIds);
    if (clauseIds.isEmpty()) {
      return content;
    }

    // 2. Fetch current titles
    Map<UUID, String> titleMap =
        clauseRepository.findAllById(clauseIds).stream()
            .collect(Collectors.toMap(Clause::getId, Clause::getTitle));

    // 3. Deep-walk and update titles (returns a new map — no JPA mutation)
    return walkAndUpdateTitles(content, titleMap);
  }

  @SuppressWarnings("unchecked")
  private void extractClauseIds(Map<String, Object> node, Set<UUID> clauseIds) {
    if ("clauseBlock".equals(node.get("type"))) {
      var attrs = (Map<String, Object>) node.get("attrs");
      if (attrs != null) {
        Object clauseIdObj = attrs.get("clauseId");
        if (clauseIdObj != null) {
          try {
            clauseIds.add(UUID.fromString(clauseIdObj.toString()));
          } catch (IllegalArgumentException ignored) {
            // Skip invalid UUIDs
          }
        }
      }
    }
    Object contentObj = node.get("content");
    if (contentObj instanceof List<?> contentList) {
      for (Object child : contentList) {
        if (child instanceof Map<?, ?> childMap) {
          extractClauseIds((Map<String, Object>) childMap, clauseIds);
        }
      }
    }
  }

  /**
   * Structural copy: creates new maps at each tree level, but leaf values (strings, numbers) and
   * non-content map values (marks, attrs on non-clauseBlock nodes) share references with the
   * original. Safe because the result is only serialized to JSON and getById() is read-only.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> walkAndUpdateTitles(
      Map<String, Object> node, Map<UUID, String> titleMap) {
    var copy = new LinkedHashMap<>(node);

    if ("clauseBlock".equals(copy.get("type"))) {
      var attrs = (Map<String, Object>) copy.get("attrs");
      if (attrs != null) {
        Object clauseIdObj = attrs.get("clauseId");
        if (clauseIdObj != null) {
          try {
            UUID clauseId = UUID.fromString(clauseIdObj.toString());
            String currentTitle = titleMap.get(clauseId);
            if (currentTitle != null) {
              var attrsCopy = new LinkedHashMap<>(attrs);
              attrsCopy.put("title", currentTitle);
              copy.put("attrs", attrsCopy);
            }
          } catch (IllegalArgumentException ignored) {
            // Skip invalid UUIDs
          }
        }
      }
    }

    Object contentObj = copy.get("content");
    if (contentObj instanceof List<?> contentList) {
      List<Object> newContent = new ArrayList<>(contentList.size());
      for (Object child : contentList) {
        if (child instanceof Map<?, ?> childMap) {
          newContent.add(walkAndUpdateTitles((Map<String, Object>) childMap, titleMap));
        } else {
          newContent.add(child);
        }
      }
      copy.put("content", newContent);
    }

    return copy;
  }
}
