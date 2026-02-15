package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.CreateItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.CreateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.TemplateItemResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.TemplateResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.TemplateWithItemsResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.UpdateItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateController.UpdateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistTemplateService {

  private static final Logger log = LoggerFactory.getLogger(ChecklistTemplateService.class);

  private final ChecklistTemplateRepository templateRepository;
  private final ChecklistTemplateItemRepository itemRepository;
  private final AuditService auditService;

  public ChecklistTemplateService(
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository itemRepository,
      AuditService auditService) {
    this.templateRepository = templateRepository;
    this.itemRepository = itemRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<TemplateResponse> listAll() {
    return templateRepository.findByActiveTrueOrderBySortOrder().stream()
        .map(TemplateResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public TemplateWithItemsResponse findById(UUID id) {
    var template =
        templateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    var items =
        itemRepository.findByTemplateIdOrderBySortOrder(template.getId()).stream()
            .map(TemplateItemResponse::from)
            .toList();

    return new TemplateWithItemsResponse(TemplateResponse.from(template), items);
  }

  @Transactional
  public TemplateWithItemsResponse create(CreateTemplateRequest request) {
    String slug =
        request.slug() != null && !request.slug().isBlank()
            ? generateSlug(request.slug())
            : generateSlug(request.name());

    // Validate slug uniqueness
    if (templateRepository.findBySlug(slug).isPresent()) {
      throw new ResourceConflictException(
          "Duplicate slug", "A checklist template with slug '" + slug + "' already exists");
    }

    // Validate auto-instantiate uniqueness
    if (request.autoInstantiate()) {
      validateAutoInstantiateUniqueness(request.customerType(), null);
    }

    var template =
        new ChecklistTemplate(
            request.name(), slug, request.description(), request.customerType(), "ORG_CUSTOM");
    template.setAutoInstantiate(request.autoInstantiate());
    template.setSortOrder(request.sortOrder());
    template = templateRepository.save(template);

    // Create items
    List<ChecklistTemplateItem> savedItems = createItems(template.getId(), request.items());

    // Validate dependencies after items are saved (so they have IDs)
    validateDependencies(template.getId(), savedItems);

    log.info(
        "Created checklist template: id={}, slug={}, items={}",
        template.getId(),
        template.getSlug(),
        savedItems.size());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_template.created")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(
                Map.of(
                    "name", template.getName(),
                    "slug", template.getSlug(),
                    "customerType", template.getCustomerType()))
            .build());

    return new TemplateWithItemsResponse(
        TemplateResponse.from(template),
        savedItems.stream().map(TemplateItemResponse::from).toList());
  }

  @Transactional
  public TemplateWithItemsResponse update(UUID id, UpdateTemplateRequest request) {
    var template =
        templateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    // Validate auto-instantiate uniqueness (exclude self)
    if (request.autoInstantiate()) {
      validateAutoInstantiateUniqueness(template.getCustomerType(), template.getId());
    }

    template.setName(request.name());
    template.setDescription(request.description());
    template.setAutoInstantiate(request.autoInstantiate());
    template.setSortOrder(request.sortOrder());
    template = templateRepository.save(template);

    // Process item updates
    List<ChecklistTemplateItem> updatedItems = updateItems(template.getId(), request.items());

    // Validate dependencies
    validateDependencies(template.getId(), updatedItems);

    log.info("Updated checklist template: id={}, name={}", template.getId(), template.getName());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_template.updated")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(Map.of("name", template.getName()))
            .build());

    return new TemplateWithItemsResponse(
        TemplateResponse.from(template),
        updatedItems.stream().map(TemplateItemResponse::from).toList());
  }

  @Transactional
  public void deactivate(UUID id) {
    var template =
        templateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    template.deactivate();
    templateRepository.save(template);

    log.info(
        "Deactivated checklist template: id={}, slug={}", template.getId(), template.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_template.deleted")
            .entityType("checklist_template")
            .entityId(template.getId())
            .details(Map.of("slug", template.getSlug()))
            .build());
  }

  @Transactional
  public TemplateWithItemsResponse clone(UUID id, String newName) {
    var original =
        templateRepository
            .findOneById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", id));

    String slug = generateSlug(newName);

    // Validate slug uniqueness
    if (templateRepository.findBySlug(slug).isPresent()) {
      throw new ResourceConflictException(
          "Duplicate slug", "A checklist template with slug '" + slug + "' already exists");
    }

    var cloned =
        new ChecklistTemplate(
            newName, slug, original.getDescription(), original.getCustomerType(), "ORG_CUSTOM");
    cloned.setAutoInstantiate(false);
    cloned.setSortOrder(original.getSortOrder());
    cloned.setPackId(null);
    cloned.setPackTemplateKey(null);
    cloned = templateRepository.save(cloned);

    // Clone items and build ID mapping for dependency references
    var originalItems = itemRepository.findByTemplateIdOrderBySortOrder(original.getId());
    Map<UUID, UUID> idMapping = new HashMap<>();
    List<ChecklistTemplateItem> clonedItems = new java.util.ArrayList<>();

    for (var item : originalItems) {
      var clonedItem =
          new ChecklistTemplateItem(
              cloned.getId(),
              item.getName(),
              item.getDescription(),
              item.getSortOrder(),
              item.isRequired(),
              item.isRequiresDocument());
      clonedItem.setRequiredDocumentLabel(item.getRequiredDocumentLabel());
      clonedItem = itemRepository.save(clonedItem);
      idMapping.put(item.getId(), clonedItem.getId());
      clonedItems.add(clonedItem);
    }

    // Update dependency references using old-to-new ID mapping
    for (int i = 0; i < originalItems.size(); i++) {
      var originalItem = originalItems.get(i);
      if (originalItem.getDependsOnItemId() != null) {
        UUID newDependsOnId = idMapping.get(originalItem.getDependsOnItemId());
        if (newDependsOnId != null) {
          clonedItems.get(i).setDependsOnItemId(newDependsOnId);
          itemRepository.save(clonedItems.get(i));
        }
      }
    }

    log.info(
        "Cloned checklist template: originalId={}, newId={}, newSlug={}",
        original.getId(),
        cloned.getId(),
        cloned.getSlug());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_template.cloned")
            .entityType("checklist_template")
            .entityId(cloned.getId())
            .details(
                Map.of(
                    "name", cloned.getName(),
                    "slug", cloned.getSlug(),
                    "originalId", original.getId().toString()))
            .build());

    return new TemplateWithItemsResponse(
        TemplateResponse.from(cloned),
        clonedItems.stream().map(TemplateItemResponse::from).toList());
  }

  // --- Private helpers ---

  private String generateSlug(String name) {
    return name.toLowerCase().replaceAll("[\\s-]+", "_").replaceAll("[^a-z0-9_]", "");
  }

  private List<ChecklistTemplateItem> createItems(
      UUID templateId, List<CreateItemRequest> itemRequests) {
    if (itemRequests == null || itemRequests.isEmpty()) {
      return List.of();
    }

    return itemRequests.stream()
        .map(
            req -> {
              var item =
                  new ChecklistTemplateItem(
                      templateId,
                      req.name(),
                      req.description(),
                      req.sortOrder(),
                      req.required(),
                      req.requiresDocument());
              item.setRequiredDocumentLabel(req.requiredDocumentLabel());
              item.setDependsOnItemId(req.dependsOnItemId());
              return itemRepository.save(item);
            })
        .toList();
  }

  private List<ChecklistTemplateItem> updateItems(
      UUID templateId, List<UpdateItemRequest> itemRequests) {
    if (itemRequests == null || itemRequests.isEmpty()) {
      // Delete all existing items
      var existing = itemRepository.findByTemplateIdOrderBySortOrder(templateId);
      itemRepository.deleteAll(existing);
      return List.of();
    }

    // Collect IDs of items to keep
    List<UUID> keepIds =
        itemRequests.stream()
            .map(UpdateItemRequest::id)
            .filter(id -> id != null)
            .collect(Collectors.toList());

    // Delete orphaned items
    if (!keepIds.isEmpty()) {
      itemRepository.deleteByTemplateIdAndIdNotIn(templateId, keepIds);
    } else {
      // All items are new — delete all existing
      var existing = itemRepository.findByTemplateIdOrderBySortOrder(templateId);
      itemRepository.deleteAll(existing);
    }

    return itemRequests.stream()
        .map(
            req -> {
              if (req.id() != null) {
                // Update existing item
                var item =
                    itemRepository
                        .findOneById(req.id())
                        .orElseThrow(
                            () -> new ResourceNotFoundException("ChecklistTemplateItem", req.id()));
                item.setName(req.name());
                item.setDescription(req.description());
                item.setSortOrder(req.sortOrder());
                item.setRequired(req.required());
                item.setRequiresDocument(req.requiresDocument());
                item.setRequiredDocumentLabel(req.requiredDocumentLabel());
                item.setDependsOnItemId(req.dependsOnItemId());
                return itemRepository.save(item);
              } else {
                // Create new item
                var item =
                    new ChecklistTemplateItem(
                        templateId,
                        req.name(),
                        req.description(),
                        req.sortOrder(),
                        req.required(),
                        req.requiresDocument());
                item.setRequiredDocumentLabel(req.requiredDocumentLabel());
                item.setDependsOnItemId(req.dependsOnItemId());
                return itemRepository.save(item);
              }
            })
        .toList();
  }

  private void validateAutoInstantiateUniqueness(String customerType, UUID excludeTemplateId) {
    long count =
        templateRepository.countByCustomerTypeAndAutoInstantiateTrueAndActiveTrue(customerType);
    if (count > 0) {
      // If we're updating, check if the existing auto-instantiate template is the same one
      if (excludeTemplateId != null) {
        var existing =
            templateRepository.findByActiveTrueAndAutoInstantiateTrueAndCustomerType(customerType);
        if (existing.isPresent() && existing.get().getId().equals(excludeTemplateId)) {
          return; // It's the same template — allowed
        }
      }
      throw new ResourceConflictException(
          "Auto-instantiate conflict",
          "Only one template per customer type can have auto-instantiate enabled. "
              + "A template for customer type '"
              + customerType
              + "' already has auto-instantiate enabled.");
    }
  }

  private void validateDependencies(UUID templateId, List<ChecklistTemplateItem> items) {
    if (items.isEmpty()) {
      return;
    }

    Set<UUID> itemIds =
        items.stream().map(ChecklistTemplateItem::getId).collect(Collectors.toSet());

    // Validate cross-template dependencies
    for (var item : items) {
      if (item.getDependsOnItemId() != null) {
        if (!itemIds.contains(item.getDependsOnItemId())) {
          // Check if the dependency exists at all (could be cross-template)
          var depItem = itemRepository.findOneById(item.getDependsOnItemId());
          if (depItem.isEmpty()) {
            throw new ResourceNotFoundException("ChecklistTemplateItem", item.getDependsOnItemId());
          }
          if (!depItem.get().getTemplateId().equals(templateId)) {
            throw new ResourceConflictException(
                "Cross-template dependency",
                "Item dependencies must reference items in the same template");
          }
        }
      }
    }

    // Validate no circular dependencies
    validateNoCycles(items);
  }

  private void validateNoCycles(List<ChecklistTemplateItem> items) {
    Map<UUID, UUID> dependsOnMap = new HashMap<>();
    for (var item : items) {
      if (item.getDependsOnItemId() != null) {
        dependsOnMap.put(item.getId(), item.getDependsOnItemId());
      }
    }

    Set<UUID> visiting = new HashSet<>();
    Set<UUID> visited = new HashSet<>();

    for (var itemId : dependsOnMap.keySet()) {
      if (hasCycle(itemId, dependsOnMap, visiting, visited)) {
        throw new ResourceConflictException(
            "Circular dependency", "Checklist items have circular dependencies");
      }
    }
  }

  private boolean hasCycle(
      UUID itemId, Map<UUID, UUID> deps, Set<UUID> visiting, Set<UUID> visited) {
    if (visiting.contains(itemId)) {
      return true;
    }
    if (visited.contains(itemId)) {
      return false;
    }

    visiting.add(itemId);
    UUID dependsOn = deps.get(itemId);
    if (dependsOn != null && hasCycle(dependsOn, deps, visiting, visited)) {
      return true;
    }
    visiting.remove(itemId);
    visited.add(itemId);
    return false;
  }
}
