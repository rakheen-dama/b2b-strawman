package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceDtos.ChecklistProgressDto;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.HashMap;
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
  private final AuditService auditService;

  public ChecklistInstanceService(
      ChecklistInstanceRepository instanceRepository,
      ChecklistInstanceItemRepository instanceItemRepository,
      ChecklistTemplateRepository templateRepository,
      ChecklistTemplateItemRepository templateItemRepository,
      AuditService auditService) {
    this.instanceRepository = instanceRepository;
    this.instanceItemRepository = instanceItemRepository;
    this.templateRepository = templateRepository;
    this.templateItemRepository = templateItemRepository;
    this.auditService = auditService;
  }

  @Transactional
  public ChecklistInstance createFromTemplate(UUID templateId, UUID customerId) {
    var template =
        templateRepository
            .findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistTemplate", templateId));

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
        var item = instanceItemRepository.findById(instanceItemId).orElseThrow();
        item.setDependsOnItemId(dependsOnInstanceItemId);
        item.setStatus("BLOCKED");
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
  public ChecklistInstanceItem completeItem(
      UUID itemId, String notes, UUID documentId, UUID actorId) {
    var item =
        instanceItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("ChecklistInstanceItem", itemId));

    item.complete(actorId, notes, documentId);
    item = instanceItemRepository.save(item);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.item.completed")
            .entityType("checklist_instance_item")
            .entityId(item.getId())
            .details(
                Map.of("itemName", item.getName(), "instanceId", item.getInstanceId().toString()))
            .build());

    log.info("Completed checklist item '{}' ({})", item.getName(), item.getId());
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

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("checklist.item.skipped")
            .entityType("checklist_instance_item")
            .entityId(item.getId())
            .details(
                Map.of("itemName", item.getName(), "instanceId", item.getInstanceId().toString()))
            .build());

    log.info("Skipped checklist item '{}' ({})", item.getName(), item.getId());
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
}
