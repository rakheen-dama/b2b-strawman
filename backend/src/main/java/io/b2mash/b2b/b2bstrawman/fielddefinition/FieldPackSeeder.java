package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds field packs (JSON-defined field definitions and groups) for newly provisioned tenants.
 * Reads pack files from classpath:field-packs/*.json. Idempotent: tracks applied packs in
 * OrgSettings.fieldPackStatus.
 */
@Service
public class FieldPackSeeder {

  private static final Logger log = LoggerFactory.getLogger(FieldPackSeeder.class);
  private static final String PACK_LOCATION = "classpath:field-packs/*.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public FieldPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Seeds all available field packs for the given tenant. Must be called during or after tenant
   * provisioning when the schema and tables already exist.
   *
   * @param tenantId schema name (e.g., "tenant_abc123")
   * @param orgId Clerk organization ID
   */
  public void seedPacksForTenant(String tenantId, String orgId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
        .where(RequestScopes.ORG_ID, orgId)
        .run(() -> transactionTemplate.executeWithoutResult(tx -> doSeedPacks(tenantId)));
  }

  private void doSeedPacks(String tenantId) {
    List<FieldPackDefinition> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No field packs found on classpath for tenant {}", tenantId);
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

    for (FieldPackDefinition pack : packs) {
      if (isPackAlreadyApplied(settings, pack.packId())) {
        log.info("Pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack);
      settings.recordPackApplication(pack.packId(), pack.version());
      log.info("Applied field pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<FieldPackDefinition> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return java.util.Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  return objectMapper.readValue(
                      resource.getInputStream(), FieldPackDefinition.class);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse field pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for field packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getFieldPackStatus() == null) {
      return false;
    }
    return settings.getFieldPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  private void applyPack(FieldPackDefinition pack) {
    EntityType entityType = EntityType.valueOf(pack.entityType());

    // Create the field group
    var group = new FieldGroup(entityType, pack.group().name(), pack.group().slug());
    group.setDescription(pack.group().description());
    group.setPackId(pack.packId());
    group.setAutoApply(pack.group().autoApplyOrDefault());
    group = fieldGroupRepository.save(group);

    // Create each field definition and link to the group
    for (FieldPackField field : pack.fields()) {
      FieldType fieldType = FieldType.valueOf(field.fieldType());

      var fd = new FieldDefinition(entityType, field.name(), field.slug(), fieldType);
      fd.setDescription(field.description());
      fd.setSortOrder(field.sortOrder());
      fd.setPackId(pack.packId());
      fd.setPackFieldKey(field.slug());

      if (field.required()) {
        fd.updateMetadata(field.name(), field.description(), field.required(), field.validation());
      }

      if (field.defaultValue() != null) {
        fd.setDefaultValue(field.defaultValue());
      }
      if (field.options() != null) {
        fd.setOptions(field.options());
      }
      if (field.validation() != null) {
        fd.setValidation(field.validation());
      }

      // Set requiredForContexts from pack field (Phase 33 prerequisite contexts)
      var contexts = field.requiredForContexts();
      if (!contexts.isEmpty()) {
        fd.setRequiredForContexts(new ArrayList<>(contexts));
      }

      fd = fieldDefinitionRepository.save(fd);

      // Create the group membership
      var member = new FieldGroupMember(group.getId(), fd.getId(), field.sortOrder());
      fieldGroupMemberRepository.save(member);
    }
  }
}
