package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.ChecklistTemplateItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.ChecklistTemplateResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.CreateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.UpdateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
  public List<ChecklistTemplateResponse> listActive(String customerType) {
    List<ChecklistTemplate> templates;
    if (customerType != null && !customerType.isBlank()) {
      templates =
          templateRepository.findByActiveAndCustomerTypeInOrderBySortOrder(
              true, List.of(customerType, "ANY"));
    } else {
      templates = templateRepository.findByActiveOrderBySortOrder(true);
    }
    return templates.stream()
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
  public ChecklistTemplateResponse create(CreateChecklistTemplateRequest request) {
    String baseSlug =
        request.slug() != null && !request.slug().isBlank()
            ? request.slug()
            : ChecklistTemplate.generateSlug(request.name());
    String finalSlug = resolveUniqueSlug(baseSlug);

    var template =
        new ChecklistTemplate(
            request.name(),
            request.description(),
            finalSlug,
            request.customerType(),
            "ORG_CUSTOM",
            request.autoInstantiate());

    if (request.sortOrder() != null) {
      template.setSortOrder(request.sortOrder());
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
  public ChecklistTemplateResponse update(UUID id, UpdateChecklistTemplateRequest request) {
    var template =
        templateRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    template.update(request.name(), request.description(), request.autoInstantiate());
    if (request.sortOrder() != null) {
      template.setSortOrder(request.sortOrder());
    }
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

  @Transactional
  public ChecklistTemplateResponse cloneTemplate(UUID templateId) {
    var original =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", templateId));

    var items = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);

    String cloneSlug = resolveUniqueSlug(original.getSlug() + "-custom");

    var clone =
        new ChecklistTemplate(
            original.getName() + " (Custom)",
            original.getDescription(),
            cloneSlug,
            original.getCustomerType(),
            "ORG_CUSTOM",
            original.isAutoInstantiate());
    clone.setSortOrder(original.getSortOrder());

    clone = templateRepository.save(clone);

    // Copy items — first pass without dependencies to get new IDs
    final UUID cloneId = clone.getId();
    List<ChecklistTemplateItem> phase1 =
        items.stream()
            .map(
                item -> {
                  var newItem =
                      new ChecklistTemplateItem(
                          cloneId, item.getName(), item.getSortOrder(), item.isRequired());
                  newItem.setDescription(item.getDescription());
                  newItem.setRequiresDocument(item.isRequiresDocument());
                  newItem.setRequiredDocumentLabel(item.getRequiredDocumentLabel());
                  return newItem;
                })
            .toList();
    List<ChecklistTemplateItem> savedPhase1 = templateItemRepository.saveAll(phase1);

    // Build old→new ID map (items are in same order as original)
    Map<UUID, UUID> oldToNew = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      oldToNew.put(items.get(i).getId(), savedPhase1.get(i).getId());
    }

    // Second pass: remap dependencies using the new IDs
    for (int i = 0; i < items.size(); i++) {
      var originalItem = items.get(i);
      if (originalItem.getDependsOnItemId() != null) {
        var cloneItem = savedPhase1.get(i);
        cloneItem.setDependsOnItemId(oldToNew.get(originalItem.getDependsOnItemId()));
        templateItemRepository.save(cloneItem);
      }
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.template.cloned")
            .entityType("checklist_template")
            .entityId(clone.getId())
            .details(
                Map.of(
                    "original_id", original.getId().toString(),
                    "original_name", original.getName(),
                    "clone_name", clone.getName()))
            .build());

    log.info("Cloned checklist template: original={}, clone={}", original.getId(), clone.getId());
    return ChecklistTemplateResponse.from(clone, savedPhase1);
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
    var savedItems = templateItemRepository.saveAll(items);
    validateDependencies(savedItems);
    return savedItems;
  }

  private void validateDependencies(List<ChecklistTemplateItem> savedItems) {
    Map<UUID, UUID> dependsOn = new HashMap<>();
    Set<UUID> itemIds = new HashSet<>();
    for (ChecklistTemplateItem item : savedItems) {
      itemIds.add(item.getId());
      if (item.getDependsOnItemId() != null) {
        dependsOn.put(item.getId(), item.getDependsOnItemId());
      }
    }

    // Each dependsOnItemId must reference an item in the same template batch
    for (Map.Entry<UUID, UUID> entry : dependsOn.entrySet()) {
      if (!itemIds.contains(entry.getValue())) {
        throw new InvalidStateException(
            "Invalid dependency", "Item dependency references an item not in this template");
      }
    }

    // Detect cycles using DFS with visited + recursion stack
    Set<UUID> visited = new HashSet<>();
    Set<UUID> inStack = new HashSet<>();
    for (UUID itemId : itemIds) {
      if (!visited.contains(itemId)) {
        detectCycle(itemId, dependsOn, visited, inStack);
      }
    }
  }

  private void detectCycle(
      UUID current, Map<UUID, UUID> dependsOn, Set<UUID> visited, Set<UUID> inStack) {
    visited.add(current);
    inStack.add(current);
    UUID next = dependsOn.get(current);
    if (next != null) {
      if (inStack.contains(next)) {
        throw new InvalidStateException(
            "Circular dependency detected", "Item dependency chain forms a cycle");
      }
      if (!visited.contains(next)) {
        detectCycle(next, dependsOn, visited, inStack);
      }
    }
    inStack.remove(current);
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
