package io.b2mash.b2b.b2bstrawman.fielddefinition;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

  /** ADR-356 reconcile entity of a pack's field group (key = the group slug). */
  static final String GROUP_ENTITY = "field_group";

  /** ADR-356 reconcile entity of a pack's field definitions (key = the field slug). */
  static final String DEFINITION_ENTITY = "field_definition";

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
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate) {
    super(
        resourceResolver,
        objectMapper,
        orgSettingsRepository,
        tenantTransactionHelper,
        packVersionGate);
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
  protected PackType getPackType() {
    return PackType.FIELD;
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
  protected String getVerticalProfile(FieldPackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getPackStatus().getFieldPackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getFieldPackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, FieldPackDefinition pack) {
    settings.getPackStatus().recordPackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(FieldPackDefinition pack, Resource packResource, String tenantId) {
    EntityType entityType = EntityType.valueOf(pack.entityType());

    // Create the field group
    var group = new FieldGroup(entityType, pack.group().name(), pack.group().slug());
    group.setDescription(pack.group().description());
    group.setPackId(pack.packId());
    group.setAutoApply(pack.group().autoApplyOrDefault());
    // GAP-L-37-regression-2026-04-25: opt-in work-type scoping. When the pack JSON declares
    // applicableWorkTypes (e.g. conveyancing-za-project specifies ["CONVEYANCING"]), persist it
    // so FieldGroupService.resolveAutoApplyGroupIds(EntityType, workType) can filter at the
    // PROJECT auto-apply step. Packs that don't opt in default to null → unscoped behaviour.
    var workTypes = pack.group().applicableWorkTypesOrEmpty();
    if (!workTypes.isEmpty()) {
      group.setApplicableWorkTypes(new ArrayList<>(workTypes));
    }
    // OBS-5004: opt-in entity-value scoping. When the pack JSON declares
    // applicableEntityValues (e.g. ["TRUST"]; no shipped pack uses it since Phase 86), persist it
    // so FieldGroupService.resolveAutoApplyGroupIds(EntityType, String, String) can filter at the
    // CUSTOMER auto-apply step. Packs that don't opt in default to null → unscoped behaviour.
    var entityValues = pack.group().applicableEntityValuesOrEmpty();
    if (!entityValues.isEmpty()) {
      group.setApplicableEntityValues(new ArrayList<>(entityValues));
    }
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

      if (field.visibilityCondition() != null) {
        fd.setVisibilityCondition(field.visibilityCondition());
      }

      fd = fieldDefinitionRepository.save(fd);

      // Create the group membership
      var member = new FieldGroupMember(group.getId(), fd.getId(), field.sortOrder());
      fieldGroupMemberRepository.save(member);
    }
  }

  /**
   * ADR-356 reconcile target (628C): {@value #GROUP_ENTITY} ({@code key} = the pack's group slug)
   * or {@value #DEFINITION_ENTITY} ({@code key} = one of the pack's field slugs = {@code
   * pack_field_key}), {@code field} {@code name} or {@code description}, string {@code to} equal to
   * the value shipped in this pack. The target is the row of the pack's entity type with that slug
   * AND this pack's {@code pack_id} (definitions also {@code pack_field_key}), so firm-created
   * groups and fields ({@code pack_id} null) are never written; the one rule (current value =
   * {@code from}) keeps firm renames. Unsupported entries are WARN-logged and skipped; nothing here
   * throws at boot.
   */
  @Override
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      FieldPackDefinition pack, PackReconcileEntry entry, Resource packResource) {
    boolean group = GROUP_ENTITY.equals(entry.entity());
    if (!group && !DEFINITION_ENTITY.equals(entry.entity())) {
      return unsupported(pack, entry, "unsupported entity");
    }
    boolean nameField = "name".equals(entry.field());
    if (!(nameField || "description".equals(entry.field())) || !(entry.to() instanceof String to)) {
      return unsupported(pack, entry, "only name/description with a string 'to' are supported");
    }
    EntityType entityType = EntityType.valueOf(pack.entityType());
    return group
        ? groupTarget(pack, entry, entityType, nameField, to)
        : definitionTarget(pack, entry, entityType, nameField, to);
  }

  private Optional<PackReconcileEntry.Target> groupTarget(
      FieldPackDefinition pack,
      PackReconcileEntry entry,
      EntityType entityType,
      boolean nameField,
      String to) {
    if (!entry.key().equals(pack.group().slug())) {
      return unsupported(pack, entry, "key is not this pack's group slug");
    }
    String shipped = nameField ? pack.group().name() : pack.group().description();
    if (!to.equals(shipped)) {
      return unsupported(pack, entry, "'to' does not match the shipped value");
    }
    return fieldGroupRepository
        .findByEntityTypeAndSlug(entityType, entry.key())
        .filter(g -> pack.packId().equals(g.getPackId()))
        .map(
            g ->
                nameField
                    ? target(
                        g::getName, v -> g.updateMetadata(v, g.getDescription(), g.getSortOrder()))
                    : target(
                        g::getDescription,
                        v -> g.updateMetadata(g.getName(), v, g.getSortOrder())));
  }

  private Optional<PackReconcileEntry.Target> definitionTarget(
      FieldPackDefinition pack,
      PackReconcileEntry entry,
      EntityType entityType,
      boolean nameField,
      String to) {
    var shippedField = pack.fields().stream().filter(f -> entry.key().equals(f.slug())).findFirst();
    if (shippedField.isEmpty()) {
      return unsupported(pack, entry, "key is not a field slug of this pack");
    }
    String shipped = nameField ? shippedField.get().name() : shippedField.get().description();
    if (!to.equals(shipped)) {
      return unsupported(pack, entry, "'to' does not match the shipped value");
    }
    return fieldDefinitionRepository
        .findByEntityTypeAndSlug(entityType, entry.key())
        .filter(
            fd -> pack.packId().equals(fd.getPackId()) && entry.key().equals(fd.getPackFieldKey()))
        .map(
            fd ->
                nameField
                    ? target(
                        fd::getName,
                        v ->
                            fd.updateMetadata(
                                v, fd.getDescription(), fd.isRequired(), fd.getValidation()))
                    : target(
                        fd::getDescription,
                        v ->
                            fd.updateMetadata(
                                fd.getName(), v, fd.isRequired(), fd.getValidation())));
  }

  private Optional<PackReconcileEntry.Target> unsupported(
      FieldPackDefinition pack, PackReconcileEntry entry, String reason) {
    log.warn(
        "Reconcile entry ignored: field pack {} entry '{}'.{} (key {}) — {}",
        pack.packId(),
        entry.entity(),
        entry.field(),
        entry.key(),
        reason);
    return Optional.empty();
  }

  private static PackReconcileEntry.Target target(
      Supplier<String> current, Consumer<String> write) {
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return current.get();
      }

      @Override
      public void apply(Object to) {
        write.accept((String) to);
      }
    };
  }
}
