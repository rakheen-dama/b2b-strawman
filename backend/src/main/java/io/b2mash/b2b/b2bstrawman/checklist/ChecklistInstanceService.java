package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistInstanceResponse;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistProgressDto;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteCheck;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final AuditService auditService;
  private final CustomerRepository customerRepository;
  private final CustomerLifecycleService customerLifecycleService;
  private final MemberRepository memberRepository;
  private final PrerequisiteService prerequisiteService;
  private final NotificationService notificationService;

  public ChecklistInstanceService(
      ChecklistInstanceRepository instanceRepository,
      ChecklistInstanceItemRepository instanceItemRepository,
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository templateItemRepository,
      AuditService auditService,
      CustomerRepository customerRepository,
      CustomerLifecycleService customerLifecycleService,
      MemberRepository memberRepository,
      PrerequisiteService prerequisiteService,
      NotificationService notificationService) {
    this.instanceRepository = instanceRepository;
    this.instanceItemRepository = instanceItemRepository;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.auditService = auditService;
    this.customerRepository = customerRepository;
    this.customerLifecycleService = customerLifecycleService;
    this.memberRepository = memberRepository;
    this.prerequisiteService = prerequisiteService;
    this.notificationService = notificationService;
  }

  @Transactional
  public ChecklistInstance createFromTemplate(UUID templateId, UUID customerId) {
    var template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", templateId));

    if (instanceRepository.existsByCustomerIdAndTemplateId(customerId, templateId)) {
      throw new ResourceConflictException(
          "Duplicate checklist instance",
          "A checklist instance already exists for this customer and template");
    }

    var templateItems = templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);

    var instance = new ChecklistInstance(templateId, customerId, Instant.now());
    instance = instanceRepository.save(instance);

    // First pass: create all instance items without dependencies
    Map<UUID, UUID> templateItemIdToInstanceItemId = new HashMap<>();
    for (ChecklistTemplateItem templateItem : templateItems) {
      var item =
          new ChecklistInstanceItem(
              instance.getId(),
              templateItem.getId(),
              templateItem.getName(),
              templateItem.getDescription(),
              templateItem.getSortOrder(),
              templateItem.isRequired(),
              templateItem.isRequiresDocument(),
              templateItem.getRequiredDocumentLabel());
      item = instanceItemRepository.save(item);
      templateItemIdToInstanceItemId.put(templateItem.getId(), item.getId());
    }

    // Second pass: resolve dependencies and block dependent items
    for (ChecklistTemplateItem templateItem : templateItems) {
      if (templateItem.getDependsOnItemId() != null) {
        UUID instanceItemId = templateItemIdToInstanceItemId.get(templateItem.getId());
        UUID dependsOnInstanceItemId =
            templateItemIdToInstanceItemId.get(templateItem.getDependsOnItemId());
        var item =
            instanceItemRepository
                .findById(instanceItemId)
                .orElseThrow(
                    () -> new ResourceNotFoundException("ChecklistInstanceItem", instanceItemId));
        item.setDependsOnItemId(dependsOnInstanceItemId);
        item.block();
        instanceItemRepository.save(item);
      }
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.instance.created")
            .entityType("checklist_instance")
            .entityId(instance.getId())
            .details(
                Map.of(
                    "templateId", templateId.toString(),
                    "customerId", customerId.toString(),
                    "templateName", template.getName()))
            .build());

    log.info(
        "Created checklist instance from template '{}' for customer {}",
        template.getName(),
        customerId);
    return instance;
  }

  @Transactional
  public ChecklistInstanceResponse createFromTemplateWithItems(UUID templateId, UUID customerId) {
    var instance = createFromTemplate(templateId, customerId);
    var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
    var memberNames = resolveNames(instance, items);
    return ChecklistInstanceResponse.from(instance, items, memberNames);
  }

  @Transactional
  public ChecklistInstanceItem completeItem(
      UUID itemId, String notes, UUID documentId, UUID actorId) {
    var item =
        instanceItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    // 102.8: Dependency chain enforcement
    if (item.getDependsOnItemId() != null) {
      UUID dependsOnItemId = item.getDependsOnItemId();
      var dependency =
          instanceItemRepository
              .findById(dependsOnItemId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("ChecklistInstanceItem", dependsOnItemId));
      if (!"COMPLETED".equals(dependency.getStatus())) {
        throw new ResourceConflictException(
            "Checklist item blocked",
            "Cannot complete item — depends on '"
                + dependency.getName()
                + "' which is not yet completed");
      }
    }

    // 102.9: Document requirement validation with label
    if (item.isRequiresDocument() && documentId == null) {
      throw new InvalidStateException(
          "Document required",
          "This item requires a document upload. Please upload: "
              + item.getRequiredDocumentLabel());
    }

    item.complete(actorId, notes, documentId);
    item = instanceItemRepository.save(item);

    unblockDependentItems(item.getId());

    // 102.13: Audit with full details
    var completeDetails = new LinkedHashMap<String, Object>();
    completeDetails.put("itemName", item.getName());
    completeDetails.put("instanceId", item.getInstanceId().toString());
    if (actorId != null) {
      completeDetails.put("completedBy", actorId.toString());
    }
    if (notes != null) {
      completeDetails.put("notes", notes);
    }
    if (documentId != null) {
      completeDetails.put("documentId", documentId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.item.completed")
            .entityType("checklist_instance_item")
            .entityId(item.getId())
            .details(completeDetails)
            .build());

    log.info("Completed checklist item '{}' ({})", item.getName(), item.getId());

    // 102.10: Auto-cascade
    checkInstanceCompletion(item.getInstanceId(), actorId);

    return item;
  }

  @Transactional
  public ChecklistInstanceItem skipItem(UUID itemId, String reason, UUID actorId) {
    var item =
        instanceItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    item.skip(reason);
    item = instanceItemRepository.save(item);

    unblockDependentItems(item.getId());

    var skipDetails = new LinkedHashMap<String, Object>();
    skipDetails.put("itemName", item.getName());
    skipDetails.put("instanceId", item.getInstanceId().toString());
    if (reason != null) {
      skipDetails.put("reason", reason);
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.item.skipped")
            .entityType("checklist_instance_item")
            .entityId(item.getId())
            .details(skipDetails)
            .build());

    log.info("Skipped checklist item '{}' ({})", item.getName(), item.getId());

    // Auto-cascade: check if instance is now fully complete (all items completed or skipped)
    checkInstanceCompletion(item.getInstanceId(), actorId);

    return item;
  }

  // 102.11: Reopen item
  @Transactional
  public ChecklistInstanceItem reopenItem(UUID itemId, UUID actorId) {
    var item =
        instanceItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    UUID instanceId = item.getInstanceId();
    var instance =
        instanceRepository
            .findById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));

    boolean instanceWasCompleted = "COMPLETED".equals(instance.getStatus());

    item.reopen();
    item = instanceItemRepository.save(item);

    if (instanceWasCompleted) {
      instance.revertToInProgress();
      instanceRepository.save(instance);
      log.info(
          "Reverted checklist instance {} to IN_PROGRESS due to reopen of item '{}'",
          instance.getId(),
          item.getName());
    }

    var reopenDetails = new LinkedHashMap<String, Object>();
    reopenDetails.put("itemName", item.getName());
    reopenDetails.put("instanceId", item.getInstanceId().toString());
    if (actorId != null) {
      reopenDetails.put("reopenedBy", actorId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.item.reopened")
            .entityType("checklist_instance_item")
            .entityId(item.getId())
            .details(reopenDetails)
            .build());

    log.info("Reopened checklist item '{}' ({})", item.getName(), item.getId());
    return item;
  }

  @Transactional(readOnly = true)
  public ChecklistProgressDto getProgress(UUID instanceId) {
    var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instanceId);

    int completed = 0;
    int total = items.size();
    int requiredCompleted = 0;
    int requiredTotal = 0;

    for (ChecklistInstanceItem item : items) {
      if ("COMPLETED".equals(item.getStatus())) {
        completed++;
        if (item.isRequired()) {
          requiredCompleted++;
        }
      }
      if (item.isRequired()) {
        requiredTotal++;
      }
    }

    return new ChecklistProgressDto(completed, total, requiredCompleted, requiredTotal);
  }

  @Transactional(readOnly = true)
  public ChecklistInstance getInstance(UUID instanceId) {
    return instanceRepository
        .findById(instanceId)
        .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));
  }

  @Transactional(readOnly = true)
  public ChecklistInstanceResponse getInstanceWithItems(UUID instanceId) {
    var instance = getInstance(instanceId);
    var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
    var memberNames = resolveNames(instance, items);
    return ChecklistInstanceResponse.from(instance, items, memberNames);
  }

  @Transactional(readOnly = true)
  public List<ChecklistInstanceResponse> getInstancesWithItemsForCustomer(UUID customerId) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var instances = instanceRepository.findByCustomerId(customerId);
    if (instances.isEmpty()) {
      return List.of();
    }

    var instanceIds = instances.stream().map(ChecklistInstance::getId).toList();
    var allItems =
        instanceItemRepository.findByInstanceIdInOrderByInstanceIdAscSortOrderAsc(instanceIds);

    // Group items by instance ID
    var itemsByInstanceId = new HashMap<UUID, List<ChecklistInstanceItem>>();
    for (var item : allItems) {
      itemsByInstanceId.computeIfAbsent(item.getInstanceId(), k -> new ArrayList<>()).add(item);
    }

    // Batch-resolve all member UUIDs across all instances and items
    var allMemberIds =
        Stream.concat(
                instances.stream().map(ChecklistInstance::getCompletedBy),
                allItems.stream().map(ChecklistInstanceItem::getCompletedBy))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, String> memberNames =
        allMemberIds.isEmpty()
            ? Map.of()
            : memberRepository.findAllById(allMemberIds).stream()
                .collect(
                    Collectors.toMap(
                        Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));

    return instances.stream()
        .map(
            instance ->
                ChecklistInstanceResponse.from(
                    instance,
                    itemsByInstanceId.getOrDefault(instance.getId(), List.of()),
                    memberNames))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChecklistInstance> getInstancesForCustomer(UUID customerId) {
    return instanceRepository.findByCustomerId(customerId);
  }

  private void unblockDependentItems(UUID completedItemId) {
    var blockedDependents =
        instanceItemRepository.findByDependsOnItemIdAndStatus(completedItemId, "BLOCKED");
    for (ChecklistInstanceItem dependent : blockedDependents) {
      dependent.unblock();
      instanceItemRepository.save(dependent);
      log.info("Unblocked checklist item '{}' ({})", dependent.getName(), dependent.getId());
    }
  }

  private void checkInstanceCompletion(UUID instanceId, UUID actorId) {
    boolean anyRequiredNotComplete =
        instanceItemRepository.existsByInstanceIdAndRequiredAndStatusNotIn(
            instanceId, true, List.of("COMPLETED", "SKIPPED"));

    if (anyRequiredNotComplete) {
      return;
    }

    var instance =
        instanceRepository
            .findById(instanceId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstance", instanceId));
    if ("COMPLETED".equals(instance.getStatus())) {
      return;
    }

    instance.complete(actorId);
    instanceRepository.save(instance);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.instance.completed")
            .entityType("checklist_instance")
            .entityId(instanceId)
            .details(Map.of("customerId", instance.getCustomerId().toString()))
            .build());

    log.info("Checklist instance {} auto-completed", instanceId);

    checkLifecycleAdvance(instance.getCustomerId(), actorId);
  }

  private Map<UUID, String> resolveNames(
      ChecklistInstance instance, List<ChecklistInstanceItem> items) {
    var ids =
        Stream.concat(
                Stream.of(instance.getCompletedBy()),
                items.stream().map(ChecklistInstanceItem::getCompletedBy))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return memberRepository.findAllById(ids).stream()
        .collect(
            Collectors.toMap(
                Member::getId, m -> m.getName() != null ? m.getName() : "", (a, b) -> a));
  }

  private void checkLifecycleAdvance(UUID customerId, UUID actorId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    if (customer.getLifecycleStatus() != LifecycleStatus.ONBOARDING) {
      return;
    }

    boolean anyInstanceNotComplete =
        instanceRepository.existsByCustomerIdAndStatusNot(customerId, "COMPLETED");

    if (!anyInstanceNotComplete) {
      // Epic 242A: Check prerequisites before auto-transition
      var prereqCheck =
          prerequisiteService.checkForContext(
              PrerequisiteContext.LIFECYCLE_ACTIVATION, EntityType.CUSTOMER, customerId);
      if (!prereqCheck.passed()) {
        log.warn(
            "Customer {} has completed all checklists but has {} prerequisite violations — "
                + "auto-transition to ACTIVE blocked",
            customerId,
            prereqCheck.violations().size());
        sendPrerequisiteBlockedNotification(customer, prereqCheck);
        return;
      }

      customerLifecycleService.transition(
          customerId, "ACTIVE", "All onboarding checklists completed", actorId);
      log.info("Customer {} auto-transitioned to ACTIVE — all checklists completed", customerId);
    }
  }

  private void sendPrerequisiteBlockedNotification(
      io.b2mash.b2b.b2bstrawman.customer.Customer customer, PrerequisiteCheck prereqCheck) {
    var adminsAndOwners = memberRepository.findByOrgRoleIn(List.of("admin", "owner"));
    var title =
        "Customer \"%s\" has completed all checklist items but has %d incomplete required fields for activation."
            .formatted(customer.getName(), prereqCheck.violations().size());

    for (var member : adminsAndOwners) {
      notificationService.createIfEnabled(
          member.getId(),
          "PREREQUISITE_BLOCKED_ACTIVATION",
          title,
          null,
          "CUSTOMER",
          customer.getId(),
          null);
    }
  }
}
