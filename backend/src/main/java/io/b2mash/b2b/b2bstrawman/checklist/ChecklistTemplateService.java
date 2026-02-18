package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.ChecklistTemplateItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.ChecklistTemplateResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.CreateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.UpdateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistTemplateService {

  private static final Logger log = LoggerFactory.getLogger(ChecklistTemplateService.class);

  private final ChecklistTemplateRepository templateRepository;
  private final ChecklistTemplateItemRepository templateItemRepository;
  private final AuditService auditService;

  public ChecklistTemplateService(
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository templateItemRepository,
      AuditService auditService) {
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<ChecklistTemplateResponse> listActive() {
    return templateRepository.findByActiveOrderBySortOrder(true).stream()
        .map(
            t -> {
              var items = templateItemRepository.findByTemplateIdOrderBySortOrder(t.getId());
              return ChecklistTemplateResponse.from(t, items);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public ChecklistTemplateResponse getById(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));
    var items = templateItemRepository.findByTemplateIdOrderBySortOrder(id);
    return ChecklistTemplateResponse.from(template, items);
  }

  @Transactional
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ChecklistTemplateResponse create(CreateChecklistTemplateRequest request) {
    String baseSlug =
        request.slug() != null && !request.slug().isBlank()
            ? request.slug()
            : ChecklistTemplate.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(baseSlug);

    var template =
        new ChecklistTemplate(
            request.name(),
            finalSlug,
            request.customerType(),
            "ORG_CUSTOM",
            request.autoInstantiate());
    if (request.description() != null) {
      template.update(request.name(), request.description(), request.autoInstantiate());
    }

    try {
      template = templateRepository.save(template);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(
          "Duplicate slug", "A checklist template with slug '" + finalSlug + "' already exists");
    }

    List<ChecklistTemplateItem> savedItems = saveItems(template.getId(), request.items());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.template.created")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(Map.of("name", template.getName(), "slug", template.getSlug()))
            .build());

    log.info(
        "Created checklist template '{}' with slug '{}'", template.getName(), template.getSlug());
    return ChecklistTemplateResponse.from(template, savedItems);
  }

  @Transactional
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ChecklistTemplateResponse update(UUID id, UpdateChecklistTemplateRequest request) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    template.update(request.name(), request.description(), request.autoInstantiate());
    template = templateRepository.save(template);

    templateItemRepository.deleteByTemplateId(id);
    List<ChecklistTemplateItem> savedItems = saveItems(id, request.items());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.template.updated")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    log.info("Updated checklist template '{}'", template.getName());
    return ChecklistTemplateResponse.from(template, savedItems);
  }

  @Transactional
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public void deactivate(UUID id) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));
    template.deactivate();
    templateRepository.save(template);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.template.deleted")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    log.info("Deactivated checklist template '{}'", template.getName());
  }

  private List<ChecklistTemplateItem> saveItems(
      UUID templateId, List<ChecklistTemplateItemRequest> itemRequests) {
    if (itemRequests == null || itemRequests.isEmpty()) {
      return List.of();
    }
    var items =
        itemRequests.stream()
            .map(
                req -> {
                  var item =
                      new ChecklistTemplateItem(
                          templateId, req.name(), req.sortOrder(), req.required());
                  item.setDescription(req.description());
                  item.setRequiresDocument(req.requiresDocument());
                  item.setRequiredDocumentLabel(req.requiredDocumentLabel());
                  item.setDependsOnItemId(req.dependsOnItemId());
                  return item;
                })
            .toList();
    return templateItemRepository.saveAll(items);
  }

  private String resolveUniqueSlug(String baseSlug) {
    String finalSlug = baseSlug;
    int suffix = 2;
    while (templateRepository.findBySlug(finalSlug).isPresent()) {
      finalSlug = baseSlug + "-" + suffix;
      suffix++;
    }
    return finalSlug;
  }
}
