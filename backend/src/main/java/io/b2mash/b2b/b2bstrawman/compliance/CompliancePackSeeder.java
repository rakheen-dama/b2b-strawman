package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackDefinition.CompliancePackChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackDefinition.CompliancePackFieldDefinition;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackDefinition.CompliancePackItem;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackDefinition.CompliancePackRetentionOverride;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroup;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMember;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMemberRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicy;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds compliance packs (JSON-defined checklist templates with field definitions and retention
 * policies) for newly provisioned tenants. Reads pack files from
 * classpath:compliance-packs/&#42;/pack.json. Idempotent: tracks applied packs in
 * OrgSettings.compliancePackStatus.
 */
@Service
public class CompliancePackSeeder {

  private static final Logger log = LoggerFactory.getLogger(CompliancePackSeeder.class);
  private static final String PACK_LOCATION = "classpath:compliance-packs/*/pack.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final ChecklistTemplateRepository checklistTemplateRepository;
  private final ChecklistTemplateItemRepository checklistTemplateItemRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final RetentionPolicyRepository retentionPolicyRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public CompliancePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      ChecklistTemplateRepository checklistTemplateRepository,
      ChecklistTemplateItemRepository checklistTemplateItemRepository,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      RetentionPolicyRepository retentionPolicyRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.checklistTemplateRepository = checklistTemplateRepository;
    this.checklistTemplateItemRepository = checklistTemplateItemRepository;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.retentionPolicyRepository = retentionPolicyRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public void seedPacksForTenant(String tenantId, String orgId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .run(() -> transactionTemplate.executeWithoutResult(tx -> doSeedPacks(tenantId)));
  }

  private void doSeedPacks(String tenantId) {
    List<CompliancePackDefinition> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No compliance packs found on classpath for tenant {}", tenantId);
      return;
    }

    var settings =
        orgSettingsRepository
            .findForCurrentTenant()
            .orElseGet(
                () -> {
                  var newSettings = new OrgSettings("USD");
                  return orgSettingsRepository.save(newSettings);
                });

    for (CompliancePackDefinition pack : packs) {
      if (isPackAlreadyApplied(settings, pack.packId())) {
        log.info(
            "Compliance pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack);
      settings.recordCompliancePackApplication(pack.packId(), pack.version());
      log.info(
          "Applied compliance pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<CompliancePackDefinition> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  return objectMapper.readValue(
                      resource.getInputStream(), CompliancePackDefinition.class);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse compliance pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for compliance packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getCompliancePackStatus() == null) {
      return false;
    }
    return settings.getCompliancePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  private void applyPack(CompliancePackDefinition pack) {
    // 1. Create checklist template
    CompliancePackChecklistTemplate tpl = pack.checklistTemplate();
    var template =
        new ChecklistTemplate(
            tpl.name(),
            pack.description(),
            tpl.slug(),
            pack.customerType(),
            "PLATFORM",
            tpl.autoInstantiate());
    template.setPackId(pack.packId());
    template.setPackTemplateKey(tpl.slug());

    // FICA packs (autoInstantiate=false) should be inactive by default
    if (!tpl.autoInstantiate()) {
      template.setActive(false);
    }

    template = checklistTemplateRepository.save(template);

    // 2. Create checklist template items (first pass — no dependencies)
    Map<String, UUID> itemKeyToId = new HashMap<>();
    Map<String, ChecklistTemplateItem> itemKeyToEntity = new HashMap<>();

    for (CompliancePackItem item : tpl.items()) {
      var templateItem =
          new ChecklistTemplateItem(
              template.getId(), item.name(), item.sortOrder(), item.required());
      templateItem.setDescription(item.description());
      if (item.requiresDocument()) {
        templateItem.setRequiresDocument(true);
        templateItem.setRequiredDocumentLabel(item.requiredDocumentLabel());
      }
      templateItem = checklistTemplateItemRepository.save(templateItem);

      String itemKey = ChecklistTemplate.generateSlug(item.name());
      itemKeyToId.put(itemKey, templateItem.getId());
      itemKeyToEntity.put(itemKey, templateItem);
    }

    // 3. Second pass — resolve dependencies
    for (CompliancePackItem item : tpl.items()) {
      if (item.dependsOnItemKey() != null) {
        String thisKey = ChecklistTemplate.generateSlug(item.name());
        UUID dependsOnId = itemKeyToId.get(item.dependsOnItemKey());
        if (dependsOnId != null) {
          ChecklistTemplateItem entity = itemKeyToEntity.get(thisKey);
          entity.setDependsOnItemId(dependsOnId);
          checklistTemplateItemRepository.save(entity);
        } else {
          log.warn(
              "Dependency key '{}' not found for item '{}' in pack {}",
              item.dependsOnItemKey(),
              item.name(),
              pack.packId());
        }
      }
    }

    // 4. Cross-seed field definitions if present
    if (pack.fieldDefinitions() != null && !pack.fieldDefinitions().isEmpty()) {
      seedFieldDefinitions(pack.packId(), pack.fieldDefinitions());
    }

    // 5. Create retention policies if present
    if (pack.retentionOverrides() != null && !pack.retentionOverrides().isEmpty()) {
      seedRetentionPolicies(pack.retentionOverrides());
    }
  }

  private void seedFieldDefinitions(String packId, List<CompliancePackFieldDefinition> fieldDefs) {
    // Group field definitions by groupName
    Map<String, List<CompliancePackFieldDefinition>> byGroup = new HashMap<>();
    for (CompliancePackFieldDefinition fd : fieldDefs) {
      byGroup.computeIfAbsent(fd.groupName(), k -> new java.util.ArrayList<>()).add(fd);
    }

    for (Map.Entry<String, List<CompliancePackFieldDefinition>> entry : byGroup.entrySet()) {
      String groupName = entry.getKey();
      String groupSlug = FieldDefinition.generateSlug(groupName);

      // Create the field group
      var group = new FieldGroup(EntityType.CUSTOMER, groupName, groupSlug);
      group.setPackId(packId);
      group = fieldGroupRepository.save(group);

      // Create each field definition and link to the group
      int sortOrder = 0;
      for (CompliancePackFieldDefinition fd : entry.getValue()) {
        FieldType fieldType =
            "SELECT".equals(fd.fieldType())
                ? FieldType.DROPDOWN
                : FieldType.valueOf(fd.fieldType());

        var fieldDef =
            new FieldDefinition(EntityType.CUSTOMER, fd.label(), fd.fieldKey(), fieldType);
        fieldDef.setSortOrder(sortOrder);
        fieldDef.setPackId(packId);
        fieldDef.setPackFieldKey(fd.fieldKey());

        if (fd.options() != null && !fd.options().isEmpty()) {
          List<Map<String, String>> opts =
              fd.options().stream().map(o -> Map.of("value", o, "label", o)).toList();
          fieldDef.setOptions(opts);
        }

        fieldDef = fieldDefinitionRepository.save(fieldDef);

        // Create the group membership
        var member = new FieldGroupMember(group.getId(), fieldDef.getId(), sortOrder);
        fieldGroupMemberRepository.save(member);

        sortOrder++;
      }
    }
  }

  private void seedRetentionPolicies(List<CompliancePackRetentionOverride> overrides) {
    for (CompliancePackRetentionOverride override : overrides) {
      // Check for existing policy (unique constraint on record_type + trigger_event)
      var existing =
          retentionPolicyRepository.findByRecordTypeAndTriggerEvent(
              override.recordType(), override.triggerEvent());
      if (existing.isEmpty()) {
        var policy =
            new RetentionPolicy(
                override.recordType(),
                override.retentionDays(),
                override.triggerEvent(),
                override.action());
        retentionPolicyRepository.save(policy);
      }
    }
  }
}
