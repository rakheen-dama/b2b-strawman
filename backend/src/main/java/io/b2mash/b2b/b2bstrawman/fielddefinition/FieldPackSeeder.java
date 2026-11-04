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
import java.util.UUID;
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
 *
 * <p>Existing tenants receive pack changes only through the ADR-356 {@code reconcile} block, which
 * supports two steps:
 *
 * <ul>
 *   <li><b>Rename / re-describe</b> (628C): {@value #GROUP_ENTITY} or {@value #DEFINITION_ENTITY}
 *       entries with {@code field} {@code name} / {@code description}, one rule (see {@link
 *       #resolveReconcileTarget}).
 *   <li><b>Add missing field definitions</b> (G88-F12): {@value #DEFINITION_ENTITY} entries with
 *       {@code field} {@value #MEMBERSHIP_FIELD}, {@code key} = a field slug of this pack and
 *       {@code to} = this pack's group slug ({@code from} unused, may be null). Modelled on the
 *       626G {@code clause_template_association} upsert: additive and skip-existing. A slug no row
 *       owns yet is created exactly as {@link #applyPack} creates it (pack-owned, shipped metadata)
 *       and added to the pack's group at its shipped sort order; a slug some row already owns — a
 *       pack-owned row (renamed, re-grouped or deactivated by the firm) or a firm-created field —
 *       is never written, re-grouped or deleted. See {@link #applyReconcileEntry}.
 * </ul>
 */
@Service
public class FieldPackSeeder extends AbstractPackSeeder<FieldPackDefinition> {

  private static final String PACK_LOCATION = "classpath:field-packs/*.json";

  /** ADR-356 reconcile entity of a pack's field group (key = the group slug). */
  static final String GROUP_ENTITY = "field_group";

  /** ADR-356 reconcile entity of a pack's field definitions (key = the field slug). */
  static final String DEFINITION_ENTITY = "field_definition";

  /**
   * ADR-356 reconcile field of the "add missing field definitions" step (G88-F12): {@code key} = a
   * field slug of the pack, {@code to} = the pack's group slug.
   */
  static final String MEMBERSHIP_FIELD = "membership";

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
      createDefinitionInGroup(pack, entityType, field, group.getId());
    }
  }

  /**
   * Creates one pack-owned field definition from the pack JSON and its membership of {@code
   * groupId} at the field's sort order — the single creation path for fresh installs ({@link
   * #applyPack}) and the add-missing reconcile step ({@link #applyReconcileEntry}).
   */
  private void createDefinitionInGroup(
      FieldPackDefinition pack, EntityType entityType, FieldPackField field, UUID groupId) {
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
    fieldGroupMemberRepository.save(new FieldGroupMember(groupId, fd.getId(), field.sortOrder()));
  }

  /**
   * ADR-356 per-entry step. {@value #DEFINITION_ENTITY} {@value #MEMBERSHIP_FIELD} entries are the
   * "add missing field definitions" step (G88-F12); every other entry is a one-rule {@code name} /
   * {@code description} update through {@link #resolveReconcileTarget}.
   *
   * <p>Add-missing semantics, modelled on {@code ClausePackSeeder}'s 626G association upsert:
   *
   * <ul>
   *   <li>{@code key} must be a field slug of this pack and {@code to} (a string) this pack's group
   *       slug, else WARN + skip (resolver convention — never throws at boot);
   *   <li>the pack's group must exist and carry this pack's {@code pack_id}, else INFO "Reconcile
   *       skipped (no pack-seeded row)";
   *   <li>any existing row with the pack's entity type and the slug (active or not, pack-owned or
   *       firm-created — {@code uq_field_def_type_slug}) is never written: a pack-owned row already
   *       in the group is a DEBUG no-op; any other owner (a firm-created field, or a pack row the
   *       firm removed from the group) is INFO "Reconcile skipped (edited)" and left alone;
   *   <li>otherwise the definition is created exactly as a fresh install creates it and added to
   *       the group at its shipped sort order (INFO "Reconcile applied").
   * </ul>
   *
   * <p>Existing records already carrying the group in {@code applied_field_groups} pick the new
   * member up without a per-record migration (groups resolve their members at read time).
   */
  @Override
  protected void applyReconcileEntry(
      FieldPackDefinition pack, Resource packResource, PackReconcileEntry entry, String tenantId) {
    if (!DEFINITION_ENTITY.equals(entry.entity()) || !MEMBERSHIP_FIELD.equals(entry.field())) {
      super.applyReconcileEntry(pack, packResource, entry, tenantId);
      return;
    }
    var shippedField = pack.fields().stream().filter(f -> entry.key().equals(f.slug())).findFirst();
    if (shippedField.isEmpty()) {
      unsupported(pack, entry, "key is not a field slug of this pack");
      return;
    }
    if (!(entry.to() instanceof String groupSlug) || !groupSlug.equals(pack.group().slug())) {
      unsupported(pack, entry, "'to' must be this pack's group slug");
      return;
    }
    EntityType entityType = EntityType.valueOf(pack.entityType());
    var group =
        fieldGroupRepository
            .findByEntityTypeAndSlug(entityType, groupSlug)
            .filter(g -> pack.packId().equals(g.getPackId()));
    if (group.isEmpty()) {
      logMembership("Reconcile skipped (no pack-seeded row)", "", tenantId, pack, entry);
      return;
    }
    var existing = fieldDefinitionRepository.findByEntityTypeAndSlug(entityType, entry.key());
    if (existing.isPresent()) {
      var fd = existing.get();
      boolean packOwned =
          pack.packId().equals(fd.getPackId()) && entry.key().equals(fd.getPackFieldKey());
      boolean inGroup =
          fieldGroupMemberRepository
              .findByFieldGroupIdOrderBySortOrder(group.get().getId())
              .stream()
              .anyMatch(m -> fd.getId().equals(m.getFieldDefinitionId()));
      if (packOwned && inGroup) {
        log.debug(
            "Reconcile no-op: tenant={} pack={} {} '{}' -> '{}' field {} already current",
            tenantId,
            pack.packId(),
            entry.entity(),
            entry.key(),
            groupSlug,
            entry.field());
      } else {
        logMembership(
            "Reconcile skipped (edited)",
            " — the slug is owned by an existing row, left alone",
            tenantId,
            pack,
            entry);
      }
      return;
    }
    createDefinitionInGroup(pack, entityType, shippedField.get(), group.get().getId());
    logMembership("Reconcile applied", " added", tenantId, pack, entry);
  }

  private void logMembership(
      String outcome,
      String suffix,
      String tenantId,
      FieldPackDefinition pack,
      PackReconcileEntry entry) {
    log.info(
        "{}: tenant={} pack={} {} '{}' -> '{}' field {}{}",
        outcome,
        tenantId,
        pack.packId(),
        entry.entity(),
        entry.key(),
        entry.to(),
        entry.field(),
        suffix);
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
