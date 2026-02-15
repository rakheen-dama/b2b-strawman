package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateDetailResponse;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.TemplateListResponse;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateController.UpdateTemplateRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTemplateService {

  private static final Logger log = LoggerFactory.getLogger(DocumentTemplateService.class);

  private final DocumentTemplateRepository documentTemplateRepository;
  private final AuditService auditService;

  public DocumentTemplateService(
      DocumentTemplateRepository documentTemplateRepository, AuditService auditService) {
    this.documentTemplateRepository = documentTemplateRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<TemplateListResponse> listAll() {
    return documentTemplateRepository.findByActiveTrueOrderBySortOrder().stream()
        .map(TemplateListResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TemplateListResponse> listByCategory(TemplateCategory category) {
    return documentTemplateRepository.findByCategoryAndActiveTrueOrderBySortOrder(category).stream()
        .map(TemplateListResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TemplateListResponse> listByEntityType(TemplateEntityType entityType) {
    return documentTemplateRepository
        .findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder(entityType)
        .stream()
        .map(TemplateListResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public TemplateDetailResponse getById(UUID id) {
    var dt =
        documentTemplateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));
    return TemplateDetailResponse.from(dt);
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

    var dt =
        new DocumentTemplate(entityType, request.name(), finalSlug, category, request.content());
    dt.setDescription(request.description());
    dt.setCss(request.css());

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

  @Transactional
  public TemplateDetailResponse update(UUID id, UpdateTemplateRequest request) {
    var dt =
        documentTemplateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));

    dt.updateContent(
        request.name() != null ? request.name() : dt.getName(),
        request.description() != null ? request.description() : dt.getDescription(),
        request.content() != null ? request.content() : dt.getContent(),
        request.css() != null ? request.css() : dt.getCss());

    if (request.sortOrder() != null) {
      dt.setSortOrder(request.sortOrder());
    }

    dt = documentTemplateRepository.save(dt);

    log.info("Updated document template: id={}, name={}", dt.getId(), dt.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.updated")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(Map.of("name", dt.getName()))
            .build());

    return TemplateDetailResponse.from(dt);
  }

  @Transactional
  public void deactivate(UUID id) {
    var dt =
        documentTemplateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentTemplate", id));

    dt.deactivate();
    documentTemplateRepository.save(dt);

    log.info("Deactivated document template: id={}, slug={}", dt.getId(), dt.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("template.deleted")
            .entityType("document_template")
            .entityId(dt.getId())
            .details(Map.of("slug", dt.getSlug()))
            .build());
  }

  /**
   * Best-effort slug uniqueness resolution. There is an inherent TOCTOU race between the
   * findBySlug() check and the subsequent INSERT — concurrent requests creating templates with the
   * same name may both pass this check. The DB unique constraint on slug is the authoritative
   * guard; a DataIntegrityViolationException from save() is caught in create() and surfaced as a
   * 409 Conflict, which is acceptable behavior for concurrent same-name creation.
   */
  private String resolveUniqueSlug(String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (documentTemplateRepository.findBySlug(finalSlug).isPresent()) {
      finalSlug = baseSlug + "-" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
