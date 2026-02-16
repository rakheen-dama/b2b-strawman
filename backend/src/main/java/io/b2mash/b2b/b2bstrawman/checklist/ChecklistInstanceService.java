package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceController.InstanceProgress;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceController.InstanceResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceController.InstanceWithItemsResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceController.ItemResponse;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistInstanceService {

  private static final Logger log = LoggerFactory.getLogger(ChecklistInstanceService.class);

  private final ChecklistInstanceRepository instanceRepository;
  private final ChecklistInstanceItemRepository itemRepository;
  private final ChecklistTemplateRepository templateRepository;
  private final ChecklistTemplateItemRepository templateItemRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;

  public ChecklistInstanceService(
      ChecklistInstanceRepository instanceRepository,
      ChecklistInstanceItemRepository itemRepository,
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository templateItemRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.instanceRepository = instanceRepository;
    this.itemRepository = itemRepository;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
  }

  @Transactional
  public InstanceWithItemsResponse instantiate(UUID customerId, UUID templateId) {
    // Validate customer exists
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Check for duplicate instance
    if (instanceRepository.existsByCustomerIdAndTemplateId(customerId, templateId)) {
      throw new ResourceConflictException(
          "Duplicate checklist instance",
          "A checklist instance already exists for this customer and template");
    }

    // Load template
    var template =
        templateRepository
            .findOneById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", templateId));

    // Load template items
    var templateItems = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);

    // Create instance
    var instance =
        instanceRepository.save(new ChecklistInstance(templateId, customerId, "IN_PROGRESS"));

    // Build mapping from template item ID to instance item ID for dependency resolution
    Map<UUID, UUID> templateToInstanceItemMap = new HashMap<>();

    // First pass: create all items without dependency resolution
    var items = new java.util.ArrayList<ChecklistInstanceItem>();
    for (var templateItem : templateItems) {
      var item =
          new ChecklistInstanceItem(
              instance.getId(),
              templateItem.getId(),
              templateItem.getName(),
              templateItem.getSortOrder(),
              templateItem.isRequired());
      item.setDescription(templateItem.getDescription());
      item.setRequiresDocument(templateItem.isRequiresDocument());
      item.setRequiredDocumentLabel(templateItem.getRequiredDocumentLabel());
      // Dependencies handled in second pass
      item = itemRepository.save(item);
      templateToInstanceItemMap.put(templateItem.getId(), item.getId());
      items.add(item);
    }

    // Second pass: resolve dependencies (template item IDs -> instance item IDs)
    for (int i = 0; i < templateItems.size(); i++) {
      var templateItem = templateItems.get(i);
      if (templateItem.getDependsOnItemId() != null) {
        UUID instanceDepId = templateToInstanceItemMap.get(templateItem.getDependsOnItemId());
        if (instanceDepId != null) {
          var item = items.get(i);
          item.setDependsOnItemId(instanceDepId);
          item.block();
          itemRepository.save(item);
        }
      }
    }

    log.info(
        "Instantiated checklist: instanceId={}, templateId={}, customerId={}, items={}",
        instance.getId(),
        templateId,
        customerId,
        items.size());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_instance.created")
            .entityType("checklist_instance")
            .entityId(instance.getId())
            .details(
                Map.of(
                    "customerId", customerId.toString(),
                    "templateId", templateId.toString(),
                    "templateName", template.getName()))
            .build());

    var itemResponses = items.stream().map(ItemResponse::from).toList();
    var progress = calculateProgress(items);
    return new InstanceWithItemsResponse(
        InstanceResponse.from(instance, template.getName(), progress), itemResponses);
  }

  @Transactional
  public ItemResponse completeItem(UUID itemId, String notes, UUID documentId) {
    var item =
        itemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    // Validate blocked status
    if ("BLOCKED".equals(item.getStatus())) {
      throw new ResourceConflictException(
          "Item blocked", "Cannot complete a blocked item — dependency not yet completed");
    }

    // Validate document requirement
    if (item.isRequiresDocument() && documentId == null) {
      throw new InvalidStateException(
          "Document required",
          "This item requires a document reference. Provide documentId to complete.");
    }

    // Validate dependency if present
    UUID dependsOnItemId = item.getDependsOnItemId();
    if (dependsOnItemId != null) {
      var dependency =
          itemRepository
              .findOneById(dependsOnItemId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("ChecklistInstanceItem", dependsOnItemId));
      if (!"COMPLETED".equals(dependency.getStatus())) {
        throw new ResourceConflictException(
            "Dependency not met",
            "Dependency item must be completed before this item can be completed");
      }
    }

    UUID completedBy = RequestScopes.requireMemberId();
    item.complete(completedBy, Instant.now(), notes, documentId);
    itemRepository.save(item);

    // Unblock dependents
    var dependents = itemRepository.findByDependsOnItemId(itemId);
    for (var dependent : dependents) {
      if ("BLOCKED".equals(dependent.getStatus())) {
        dependent.unblock();
        itemRepository.save(dependent);
      }
    }

    // Check instance completion
    checkAndCompleteInstance(item.getInstanceId());

    return ItemResponse.from(item);
  }

  @Transactional
  public ItemResponse skipItem(UUID itemId, String reason) {
    var item =
        itemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    if (item.isRequired()) {
      throw new ResourceConflictException(
          "Cannot skip required item", "Required items cannot be skipped");
    }

    item.skip(reason);
    itemRepository.save(item);

    return ItemResponse.from(item);
  }

  @Transactional
  public ItemResponse reopenItem(UUID itemId) {
    var item =
        itemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    if (!"COMPLETED".equals(item.getStatus()) && !"SKIPPED".equals(item.getStatus())) {
      throw new ResourceConflictException(
          "Cannot reopen item", "Only completed or skipped items can be reopened");
    }

    UUID instanceId = item.getInstanceId();
    item.reopen();
    itemRepository.save(item);

    // Re-block dependents
    var dependents = itemRepository.findByDependsOnItemId(itemId);
    for (var dependent : dependents) {
      if (!"BLOCKED".equals(dependent.getStatus())) {
        dependent.block();
        itemRepository.save(dependent);
      }
    }

    // Reopen instance if it was completed
    var instance =
        instanceRepository
            .findOneById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));
    if ("COMPLETED".equals(instance.getStatus())) {
      instance.reopen();
      instanceRepository.save(instance);
    }

    return ItemResponse.from(item);
  }

  @Transactional(readOnly = true)
  public List<InstanceResponse> findByCustomerId(UUID customerId) {
    var instances = instanceRepository.findByCustomerIdOrderByStartedAtDesc(customerId);

    // Batch-load template names in a single query
    var templateIds = instances.stream().map(ChecklistInstance::getTemplateId).distinct().toList();
    Map<UUID, String> templateMap = new HashMap<>();
    if (!templateIds.isEmpty()) {
      templateRepository
          .findByIdIn(templateIds)
          .forEach(t -> templateMap.put(t.getId(), t.getName()));
    }

    // Batch-load all items for all instances to avoid N+1 in calculateProgress
    var instanceIds = instances.stream().map(ChecklistInstance::getId).toList();
    final Map<UUID, List<ChecklistInstanceItem>> itemsByInstance;
    if (!instanceIds.isEmpty()) {
      var allItems = itemRepository.findByInstanceIdInOrderBySortOrder(instanceIds);
      itemsByInstance =
          allItems.stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(ChecklistInstanceItem::getInstanceId));
    } else {
      itemsByInstance = new HashMap<>();
    }

    return instances.stream()
        .map(
            ci -> {
              var items = itemsByInstance.getOrDefault(ci.getId(), List.of());
              var progress = calculateProgress(items);
              String templateName = templateMap.getOrDefault(ci.getTemplateId(), "Unknown");
              return InstanceResponse.from(ci, templateName, progress);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public InstanceWithItemsResponse findByIdWithItems(UUID instanceId) {
    var instance =
        instanceRepository
            .findOneById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));

    var items = itemRepository.findByInstanceIdOrderBySortOrder(instanceId);
    var itemResponses = items.stream().map(ItemResponse::from).toList();

    String templateName =
        templateRepository
            .findOneById(instance.getTemplateId())
            .map(ChecklistTemplate::getName)
            .orElse("Unknown");

    var progress = calculateProgress(items);
    return new InstanceWithItemsResponse(
        InstanceResponse.from(instance, templateName, progress), itemResponses);
  }

  @Transactional
  public void autoInstantiate(UUID customerId, String customerType) {
    var templates = templateRepository.findAutoInstantiateTemplatesForCustomerType(customerType);
    for (var template : templates) {
      // Check if instance already exists (idempotency)
      if (!instanceRepository.existsByCustomerIdAndTemplateId(customerId, template.getId())) {
        instantiate(customerId, template.getId());
      }
    }
  }

  // --- Private helpers ---

  private void checkAndCompleteInstance(UUID instanceId) {
    long requiredCount = itemRepository.countByInstanceIdAndRequired(instanceId, true);
    long completedRequiredCount =
        itemRepository.countByInstanceIdAndRequiredAndStatus(instanceId, true, "COMPLETED");

    if (requiredCount > 0 && requiredCount == completedRequiredCount) {
      var instance =
          instanceRepository
              .findOneById(instanceId)
              .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));

      if (!"COMPLETED".equals(instance.getStatus())) {
        UUID completedBy = RequestScopes.requireMemberId();
        instance.complete(completedBy, Instant.now());
        instanceRepository.save(instance);

        log.info(
            "Checklist instance completed: instanceId={}, customerId={}",
            instanceId,
            instance.getCustomerId());

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("checklist_instance.completed")
                .entityType("checklist_instance")
                .entityId(instanceId)
                .details(
                    Map.of(
                        "customerId", instance.getCustomerId().toString(),
                        "templateId", instance.getTemplateId().toString()))
                .build());

        checkAndTransitionCustomer(instance.getCustomerId());
      }
    }
  }

  private void checkAndTransitionCustomer(UUID customerId) {
    var customer =
        customerRepository
            .findOneById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    if (!"ONBOARDING".equals(customer.getLifecycleStatus())) {
      return;
    }

    // Count instances that are NOT completed or cancelled
    long incompleteCount =
        instanceRepository.countByCustomerIdAndStatusNotIn(
            customerId, List.of("COMPLETED", "CANCELLED"));

    if (incompleteCount == 0) {
      UUID changedBy = RequestScopes.requireMemberId();
      Instant now = Instant.now();
      customer.transitionLifecycle("ACTIVE", changedBy, now, null);
      customerRepository.save(customer);

      log.info("Customer auto-transitioned to ACTIVE: customerId={}", customerId);

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("customer.lifecycle_auto_transition")
              .entityType("customer")
              .entityId(customerId)
              .details(
                  Map.of(
                      "old_status", "ONBOARDING",
                      "new_status", "ACTIVE",
                      "reason", "All onboarding checklists completed"))
              .build());
    }
  }

  private InstanceProgress calculateProgress(List<ChecklistInstanceItem> items) {
    int total = items.size();
    int completed = (int) items.stream().filter(i -> "COMPLETED".equals(i.getStatus())).count();
    int required = (int) items.stream().filter(ChecklistInstanceItem::isRequired).count();
    int requiredCompleted =
        (int)
            items.stream().filter(i -> i.isRequired() && "COMPLETED".equals(i.getStatus())).count();
    return new InstanceProgress(total, completed, required, requiredCompleted);
  }
}
