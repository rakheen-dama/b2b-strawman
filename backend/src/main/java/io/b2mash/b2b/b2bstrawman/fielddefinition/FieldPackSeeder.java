package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.ArrayList;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds field packs (JSON-defined field definitions and groups) for newly provisioned tenants.
 * Reads pack files from classpath:field-packs/*.json. Idempotent: tracks applied packs in
 * OrgSettings.fieldPackStatus.
 */
@Service
public class FieldPackSeeder extends AbstractPackSeeder<FieldPackDefinition> {

  private static final String PACK_LOCATION = "classpath:field-packs/*.json";

  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldGroupMemberRepository fieldGroupMemberRepository;

  public FieldPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      FieldDefinitionRepository fieldDefinitionRepository,
      FieldGroupRepository fieldGroupRepository,
      FieldGroupMemberRepository fieldGroupMemberRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.fieldDefinitionRepository = fieldDefinitionRepository;
    this.fieldGroupRepository = fieldGroupRepository;
    this.fieldGroupMemberRepository = fieldGroupMemberRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<FieldPackDefinition> getPackDefinitionType() {
    return FieldPackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "field";
  }

  @Override
  protected String getPackId(FieldPackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(FieldPackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getFieldPackStatus() == null) {
      return false;
    }
    return settings.getFieldPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, FieldPackDefinition pack) {
    settings.recordPackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(FieldPackDefinition pack, Resource packResource, String tenantId) {
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
