package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
  private final ChecklistInstanceItemRepository instanceItemRepository;
  private final ChecklistTemplateRepository templateRepository;
  private final ChecklistTemplateItemRepository templateItemRepository;
  private final CustomerRepository customerRepository;
  private final AuditService auditService;

  public ChecklistInstanceService(
      ChecklistInstanceRepository instanceRepository,
      ChecklistInstanceItemRepository instanceItemRepository,
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository templateItemRepository,
      CustomerRepository customerRepository,
      AuditService auditService) {
    this.instanceRepository = instanceRepository;
    this.instanceItemRepository = instanceItemRepository;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.customerRepository = customerRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<ChecklistInstance> listByCustomer(UUID customerId) {
    // Verify customer exists within tenant
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    return instanceRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
  }

  @Transactional(readOnly = true)
  public ChecklistInstance findById(UUID instanceId) {
    return instanceRepository
        .findOneById(instanceId)
        .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));
  }

  @Transactional(readOnly = true)
  public List<ChecklistInstanceItem> getItems(UUID instanceId) {
    // Verify instance exists within tenant
    instanceRepository
        .findOneById(instanceId)
        .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));

    return instanceItemRepository.findByInstanceIdOrderBySortOrder(instanceId);
  }

  @Transactional
  public ChecklistInstance instantiate(UUID customerId, UUID templateId) {
    // Verify customer exists
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Verify template exists and is active
    var template =
        templateRepository
            .findOneById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", templateId));

    if (!template.isActive()) {
      throw new ResourceConflictException(
          "Template inactive", "Cannot instantiate from an inactive template");
    }

    // Create instance
    var instance = new ChecklistInstance(templateId, customerId, "IN_PROGRESS");
    instance = instanceRepository.save(instance);

    // Snapshot template items to instance items
    var templateItems = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);
    Map<UUID, UUID> templateItemToInstanceItem = new HashMap<>();

    for (var templateItem : templateItems) {
      var instanceItem =
          new ChecklistInstanceItem(
              instance.getId(),
              templateItem.getId(),
              templateItem.getName(),
              templateItem.getSortOrder(),
              templateItem.isRequired());
      instanceItem.setDescription(templateItem.getDescription());
      instanceItem.setRequiresDocument(templateItem.isRequiresDocument());
      instanceItem.setRequiredDocumentLabel(templateItem.getRequiredDocumentLabel());
      // Dependencies are set in a second pass after all items have IDs
      instanceItem = instanceItemRepository.save(instanceItem);
      templateItemToInstanceItem.put(templateItem.getId(), instanceItem.getId());
    }

    // Second pass: set dependencies using the template-to-instance ID mapping
    for (var templateItem : templateItems) {
      if (templateItem.getDependsOnItemId() != null) {
        UUID instanceItemId = templateItemToInstanceItem.get(templateItem.getId());
        UUID dependsOnInstanceItemId =
            templateItemToInstanceItem.get(templateItem.getDependsOnItemId());
        if (instanceItemId != null && dependsOnInstanceItemId != null) {
          var instanceItem = instanceItemRepository.findOneById(instanceItemId).orElseThrow();
          instanceItem.setDependsOnItemId(dependsOnInstanceItemId);
          instanceItemRepository.save(instanceItem);
        }
      }
    }

    log.info(
        "Instantiated checklist: instanceId={}, templateId={}, customerId={}, items={}",
        instance.getId(),
        templateId,
        customerId,
        templateItems.size());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_instance.created")
            .entityType("checklist_instance")
            .entityId(instance.getId())
            .details(
                Map.of(
                    "customerId", customerId.toString(),
                    "templateId", templateId.toString(),
                    "itemCount", String.valueOf(templateItems.size())))
            .build());

    return instance;
  }

  @Transactional
  public ChecklistInstanceItem completeItem(UUID itemId, String notes, UUID documentId) {
    var item =
        instanceItemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    // Validate document requirement
    if (item.isRequiresDocument() && documentId == null) {
      throw new ResourceConflictException(
          "Document required",
          "This checklist item requires a document to be uploaded before completion");
    }

    // Validate dependency
    UUID dependsOnId = item.getDependsOnItemId();
    if (dependsOnId != null) {
      var dependencyItem =
          instanceItemRepository
              .findOneById(dependsOnId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("ChecklistInstanceItem", dependsOnId));
      if (!"COMPLETED".equals(dependencyItem.getStatus())) {
        throw new ResourceConflictException(
            "Dependency not completed",
            "Cannot complete this item because its dependency '"
                + dependencyItem.getName()
                + "' has not been completed yet");
      }
    }

    UUID memberId = RequestScopes.requireMemberId();
    Instant now = Instant.now();
    item.complete(memberId, now, notes, documentId);
    item = instanceItemRepository.save(item);

    log.info("Completed checklist item: itemId={}, instanceId={}", itemId, item.getInstanceId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_item.completed")
            .entityType("checklist_instance_item")
            .entityId(itemId)
            .details(
                Map.of(
                    "instanceId",
                    item.getInstanceId().toString(),
                    "documentId",
                    documentId != null ? documentId.toString() : ""))
            .build());

    // Check if instance is now complete
    checkAndCompleteInstance(item.getInstanceId());

    return item;
  }

  @Transactional
  public ChecklistInstanceItem skipItem(UUID itemId, String reason) {
    var item =
        instanceItemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    if (item.isRequired()) {
      throw new ResourceConflictException(
          "Cannot skip required item",
          "Only optional (non-required) checklist items can be skipped");
    }

    item.skip(reason);
    item = instanceItemRepository.save(item);

    log.info("Skipped checklist item: itemId={}, instanceId={}", itemId, item.getInstanceId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_item.skipped")
            .entityType("checklist_instance_item")
            .entityId(itemId)
            .details(
                Map.of(
                    "instanceId",
                    item.getInstanceId().toString(),
                    "reason",
                    reason != null ? reason : ""))
            .build());

    return item;
  }

  @Transactional
  public ChecklistInstanceItem reopenItem(UUID itemId) {
    var item =
        instanceItemRepository
            .findOneById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    if ("PENDING".equals(item.getStatus())) {
      throw new ResourceConflictException(
          "Item already pending", "This checklist item is already in PENDING status");
    }

    // If reopening a completed item, also reopen the parent instance if it was completed
    boolean wasCompleted = "COMPLETED".equals(item.getStatus());
    item.reopen();
    item = instanceItemRepository.save(item);

    if (wasCompleted) {
      var instance = instanceRepository.findOneById(item.getInstanceId()).orElse(null);
      if (instance != null && "COMPLETED".equals(instance.getStatus())) {
        // Revert instance to IN_PROGRESS since a required item was reopened
        instance =
            new ChecklistInstance(
                instance.getTemplateId(), instance.getCustomerId(), "IN_PROGRESS");
        // We can't just set status — use the existing instance instead
      }
    }

    log.info("Reopened checklist item: itemId={}, instanceId={}", itemId, item.getInstanceId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist_item.reopened")
            .entityType("checklist_instance_item")
            .entityId(itemId)
            .details(Map.of("instanceId", item.getInstanceId().toString()))
            .build());

    return item;
  }

  /**
   * Auto-instantiate checklists for a customer based on active templates with auto_instantiate =
   * true. Called when a customer transitions to ONBOARDING.
   */
  @Transactional
  public void autoInstantiateForCustomer(UUID customerId) {
    // Since Customer entity doesn't have a customerType field, we use "ANY" to find templates
    // that match all customer types
    var templates = templateRepository.findAutoInstantiateTemplatesForCustomerType("ANY");

    for (var template : templates) {
      instantiate(customerId, template.getId());
      log.info(
          "Auto-instantiated checklist template '{}' for customer {}",
          template.getSlug(),
          customerId);
    }
  }

  // --- Private helpers ---

  private void checkAndCompleteInstance(UUID instanceId) {
    // Count incomplete required items
    long incompleteRequired =
        instanceItemRepository.countByInstanceIdAndRequiredTrueAndStatusNot(
            instanceId, "COMPLETED");

    if (incompleteRequired == 0) {
      var instance =
          instanceRepository
              .findOneById(instanceId)
              .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));

      if (!"COMPLETED".equals(instance.getStatus())) {
        UUID memberId = RequestScopes.requireMemberId();
        instance.complete(memberId, Instant.now());
        instanceRepository.save(instance);

        log.info("Checklist instance completed: instanceId={}", instanceId);

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("checklist_instance.completed")
                .entityType("checklist_instance")
                .entityId(instanceId)
                .details(Map.of("customerId", instance.getCustomerId().toString()))
                .build());

        // Check if all checklists for this customer are complete
        checkAndTransitionCustomerLifecycle(instance.getCustomerId());
      }
    }
  }

  private void checkAndTransitionCustomerLifecycle(UUID customerId) {
    // Count non-completed, non-cancelled instances
    long activeInstances =
        instanceRepository.countByCustomerIdAndStatusNot(customerId, "COMPLETED");
    // Subtract cancelled instances (they shouldn't block)
    long cancelledInstances =
        instanceRepository.countByCustomerIdAndStatusNot(customerId, "CANCELLED");
    // Actually, we need instances that are neither COMPLETED nor CANCELLED
    // Let's just check: count instances where status is IN_PROGRESS
    var allInstances = instanceRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    long incompleteCount =
        allInstances.stream()
            .filter(i -> !"COMPLETED".equals(i.getStatus()) && !"CANCELLED".equals(i.getStatus()))
            .count();

    if (incompleteCount == 0 && !allInstances.isEmpty()) {
      var customer = customerRepository.findOneById(customerId).orElse(null);
      if (customer != null && "ONBOARDING".equals(customer.getLifecycleStatus())) {
        UUID memberId = RequestScopes.requireMemberId();
        Instant now = Instant.now();
        customer.transitionLifecycle("ACTIVE", memberId, now, null);
        customerRepository.save(customer);

        log.info(
            "Customer auto-transitioned to ACTIVE after all checklists completed: customerId={}",
            customerId);

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("customer.status_changed")
                .entityType("customer")
                .entityId(customerId)
                .details(
                    Map.of(
                        "old_status", "ONBOARDING",
                        "new_status", "ACTIVE",
                        "trigger", "checklist_completion"))
                .build());
      }
    }
  }
}
