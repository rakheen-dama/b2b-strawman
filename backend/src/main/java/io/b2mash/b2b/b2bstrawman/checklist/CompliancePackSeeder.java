package io.b2mash.b2b.b2bstrawman.checklist;

import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackField;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackGroup;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicy;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds compliance packs (checklist templates, field definitions, retention policies) for newly
 * provisioned tenants. Reads pack files from classpath:compliance-packs/&#42;/pack.json.
 * Idempotent: tracks applied packs in OrgSettings.compliancePackStatus.
 */
@Service
public class CompliancePackSeeder {

  private static final Logger log = LoggerFactory.getLogger(CompliancePackSeeder.class);
  private static final String PACK_LOCATION = "classpath:compliance-packs/*/pack.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final ChecklistTemplateRepository checklistTemplateRepository;
  private final ChecklistTemplateItemRepository checklistTemplateItemRepository;
  private final FieldPackSeeder fieldPackSeeder;
  private final RetentionPolicyRepository retentionPolicyRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public CompliancePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      ChecklistTemplateRepository checklistTemplateRepository,
      ChecklistTemplateItemRepository checklistTemplateItemRepository,
      FieldPackSeeder fieldPackSeeder,
      RetentionPolicyRepository retentionPolicyRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.checklistTemplateRepository = checklistTemplateRepository;
    this.checklistTemplateItemRepository = checklistTemplateItemRepository;
    this.fieldPackSeeder = fieldPackSeeder;
    this.retentionPolicyRepository = retentionPolicyRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Seeds all available compliance packs for the given tenant. Must be called during or after
   * tenant provisioning when the schema and tables already exist.
   *
   * @param tenantId schema name (e.g., "tenant_abc123" or "tenant_shared")
   * @param orgId Clerk organization ID — used as tenant_id discriminator in shared schema
   */
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

      applyPack(pack, tenantId);
      settings.recordCompliancePackApplication(pack.packId(), pack.version());
      log.info(
          "Applied compliance pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  List<CompliancePackDefinition> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return java.util.Arrays.stream(resources)
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

  private void applyPack(CompliancePackDefinition pack, String tenantId) {
    // 1. Create ChecklistTemplate
    var templateDef = pack.checklistTemplate();
    var template =
        new ChecklistTemplate(
            templateDef.name(),
            templateDef.key(),
            templateDef.description(),
            pack.customerType(),
            "PLATFORM");
    template.setPackId(pack.packId());
    template.setPackTemplateKey(templateDef.key());
    template.setAutoInstantiate(templateDef.autoInstantiate());
    template = checklistTemplateRepository.save(template);

    // 2. Create ChecklistTemplateItems and build key-to-UUID map
    Map<String, java.util.UUID> keyToIdMap = new HashMap<>();
    for (CompliancePackChecklistItem itemDef : templateDef.items()) {
      var item =
          new ChecklistTemplateItem(
              template.getId(),
              itemDef.name(),
              itemDef.description(),
              itemDef.sortOrder(),
              itemDef.required(),
              itemDef.requiresDocument());
      if (itemDef.requiredDocumentLabel() != null) {
        item.setRequiredDocumentLabel(itemDef.requiredDocumentLabel());
      }
      item = checklistTemplateItemRepository.save(item);
      keyToIdMap.put(itemDef.key(), item.getId());
    }

    // 3. Update dependsOnItemId using the key-to-UUID map
    for (CompliancePackChecklistItem itemDef : templateDef.items()) {
      if (itemDef.dependsOnKey() != null) {
        var dependsOnId = keyToIdMap.get(itemDef.dependsOnKey());
        if (dependsOnId != null) {
          var itemId = keyToIdMap.get(itemDef.key());
          var item = checklistTemplateItemRepository.findOneById(itemId).orElseThrow();
          item.setDependsOnItemId(dependsOnId);
          checklistTemplateItemRepository.save(item);
        }
      }
    }

    // 4. Delegate field definitions to FieldPackSeeder
    if (pack.fieldDefinitions() != null && !pack.fieldDefinitions().isEmpty()) {
      delegateFieldDefinitions(pack);
    }

    // 5. Create RetentionPolicy records for retention overrides
    if (pack.retentionOverrides() != null && !pack.retentionOverrides().isEmpty()) {
      for (CompliancePackRetentionOverride override : pack.retentionOverrides()) {
        retentionPolicyRepository.save(
            new RetentionPolicy(
                override.recordType(),
                override.retentionDays(),
                override.triggerEvent(),
                override.action()));
      }
    }
  }

  private void delegateFieldDefinitions(CompliancePackDefinition pack) {
    // Group field definitions by groupSlug for delegation to FieldPackSeeder
    Map<String, List<CompliancePackFieldDefinition>> byGroup = new HashMap<>();
    for (CompliancePackFieldDefinition fd : pack.fieldDefinitions()) {
      byGroup.computeIfAbsent(fd.groupSlug(), k -> new java.util.ArrayList<>()).add(fd);
    }

    for (var entry : byGroup.entrySet()) {
      String groupSlug = entry.getKey();
      var fields = entry.getValue();
      String entityType = fields.getFirst().entityType();

      // Convert to FieldPackField list
      List<FieldPackField> fieldPackFields =
          fields.stream()
              .map(
                  fd ->
                      new FieldPackField(
                          fd.slug(),
                          fd.name(),
                          fd.fieldType(),
                          null, // description
                          false, // required
                          null, // defaultValue
                          fd.options(),
                          fd.validation(),
                          0 // sortOrder
                          ))
              .toList();

      var fieldPackGroup = new FieldPackGroup(groupSlug, groupSlug, null);
      var fieldPackDef =
          new FieldPackDefinition(
              pack.packId() + "-" + groupSlug,
              pack.version(),
              entityType,
              fieldPackGroup,
              fieldPackFields);

      fieldPackSeeder.applyFieldPack(fieldPackDef);
    }
  }
}
