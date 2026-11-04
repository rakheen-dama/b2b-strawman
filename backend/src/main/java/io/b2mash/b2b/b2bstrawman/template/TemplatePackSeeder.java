package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import io.b2mash.b2b.b2bstrawman.packs.PackInstall;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.packs.PackType;
import io.b2mash.b2b.b2bstrawman.packs.PackVersionGate;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.PackReconcileEntry;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds template packs (JSON-defined document templates) for newly provisioned tenants. Reads pack
 * files from classpath:template-packs/&#42;/pack.json. Idempotent: tracks applied packs in
 * OrgSettings.templatePackStatus.
 */
@Service
public class TemplatePackSeeder extends AbstractPackSeeder<TemplatePackDefinition> {

  private static final String PACK_LOCATION = "classpath:template-packs/*/pack.json";

  /**
   * ADR-356 reconcile entity name for pack templates (fields: content, name, description,
   * primaryEntityType, requiredContextFields).
   */
  private static final String TEMPLATE_ENTITY = "document_template";

  private static final String CONTENT_FIELD = "content";
  private static final String NAME_FIELD = "name";
  private static final String DESCRIPTION_FIELD = "description";
  private static final String PRIMARY_ENTITY_TYPE_FIELD = "primaryEntityType";
  private static final String REQUIRED_CONTEXT_FIELDS_FIELD = "requiredContextFields";

  /**
   * {@code currentValue()} of a {@code requiredContextFields} target whose body is not the shipped
   * body: never equal to {@code from} (null) nor to the {@code sha256:} {@code to}, so the one rule
   * reports LEFT_ALONE.
   */
  private static final String TENANT_EDITED_BODY = "tenant-edited-body";

  private final DocumentTemplateRepository documentTemplateRepository;
  private final PackInstallRepository packInstallRepository;

  public TemplatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper,
      PackVersionGate packVersionGate,
      PackInstallRepository packInstallRepository) {
    super(
        resourceResolver,
        objectMapper,
        orgSettingsRepository,
        tenantTransactionHelper,
        packVersionGate);
    this.documentTemplateRepository = documentTemplateRepository;
    this.packInstallRepository = packInstallRepository;
  }

  @Override
  protected String getPackResourcePattern() {
    return PACK_LOCATION;
  }

  @Override
  protected Class<TemplatePackDefinition> getPackDefinitionType() {
    return TemplatePackDefinition.class;
  }

  @Override
  protected String getPackTypeName() {
    return "template";
  }

  @Override
  protected PackType getPackType() {
    return PackType.DOCUMENT_TEMPLATE;
  }

  @Override
  protected String getPackId(TemplatePackDefinition pack) {
    return pack.packId();
  }

  @Override
  protected String getPackVersion(TemplatePackDefinition pack) {
    return String.valueOf(pack.version());
  }

  @Override
  protected String getVerticalProfile(TemplatePackDefinition pack) {
    return pack.verticalProfile();
  }

  @Override
  protected boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getPackStatus().getTemplatePackStatus() == null) {
      return false;
    }
    return settings.getPackStatus().getTemplatePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, TemplatePackDefinition pack) {
    settings.getPackStatus().recordTemplatePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(TemplatePackDefinition pack, Resource packResource, String tenantId) {
    for (TemplatePackTemplate templateDef : pack.templates()) {
      createTemplate(pack, templateDef, packResource);
    }
  }

  /**
   * Creates a single pack template row. Public delegate used by {@code TemplatePackInstaller}
   * reconciliation to deliver templates added in a newer pack version to tenants that installed an
   * earlier version. Caller is responsible for tenant scoping and duplicate checks.
   */
  public void applySingleTemplate(
      TemplatePackDefinition pack, TemplatePackTemplate templateDef, Resource packResource) {
    createTemplate(pack, templateDef, packResource);
  }

  /** Content + CSS of a pack template as defined on the classpath. */
  public record TemplateContent(Map<String, Object> content, String css) {}

  /**
   * Loads a pack template's content JSON and CSS from the classpath pack resource. Used by {@code
   * TemplatePackInstaller} reconciliation to refresh unmodified tenant templates to the current
   * pack content.
   */
  public TemplateContent loadTemplateContent(
      TemplatePackTemplate templateDef, Resource packResource) {
    String css =
        templateDef.cssFile() != null
            ? loadTemplateContentAsString(packResource, templateDef.cssFile())
            : null;

    // Load content as JSONB Map for the primary content field
    Map<String, Object> contentJson = null;
    if (templateDef.contentFile() != null && templateDef.contentFile().endsWith(".json")) {
      contentJson = loadTemplateContentAsJson(packResource, templateDef.contentFile());
    }
    if (contentJson == null) {
      throw new IllegalStateException(
          "Template definition has no JSON content file: " + templateDef.contentFile());
    }
    return new TemplateContent(contentJson, css);
  }

  /**
   * ADR-356 reconcile target for a {@code document_template} entry. {@code content} (Phase 88
   * 626D): {@code key} is the pack's {@code templateKey} ({@code pack_template_key}) and {@code to}
   * is {@code sha256:} + {@link ContentHashUtil#hashOf} of the body shipped in this pack. The
   * target is this pack install's pack-seeded row only — {@code PLATFORM}, not a clone, tagged with
   * this pack's {@code pack_install} id — so clones and firm-authored templates are never written;
   * the one rule (current body hash = {@code from}) keeps firm edits. Writes the classpath body
   * through the managed entity (css untouched) and never a content hash — {@code
   * TemplatePackInstaller} re-pins pristine rows. {@code name} / {@code description} (628C): a
   * plain string {@code to} equal to the template's {@code name} / {@code description} in this
   * pack, string equality; the same ownership, written through the managed entity (slug, body and
   * css untouched — the slug is an identifier and is never reconciled). Names are not part of the
   * install content hash, and {@code refreshIfUnmodified} does not touch them, so these entries are
   * what renames a pristine row. {@code primaryEntityType} (G88-F12): a plain string {@code to}
   * equal to the template's {@code primaryEntityType} in this pack (a {@link TemplateEntityType}
   * name); the same ownership. The entity type decides which context the body renders with, so it
   * moves only together with the shipped body: a row whose body is not the shipped body (tenant
   * edited) reports no current value and the one rule leaves it alone. List this entry after the
   * template's {@code content} entry so a body the refresh skipped is updated first. {@code
   * requiredContextFields} (Phase 89 C1): {@code from} null (no pack declared the list before
   * legal-za v11) and {@code to} = {@code sha256:} + {@link ContentHashUtil#hashOf} of the list
   * shipped in this pack (a reconcile value is scalar-only); the same ownership; like {@code
   * primaryEntityType} it moves only while the row's body is the shipped body (a tenant-edited body
   * reads as a sentinel that matches neither {@code from} nor {@code to}, so it is LEFT_ALONE), and
   * {@code apply} writes a copy of the shipped list through the managed entity. Unsupported
   * entries, a {@code to} that does not match the shipped value and ambiguous rows are WARN-logged
   * and skipped; nothing here throws at boot.
   */
  @Override
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      TemplatePackDefinition pack, PackReconcileEntry entry, Resource packResource) {
    boolean content = CONTENT_FIELD.equals(entry.field());
    boolean requiredFields = REQUIRED_CONTEXT_FIELDS_FIELD.equals(entry.field());
    boolean hashValued = content || requiredFields;
    boolean entityType = PRIMARY_ENTITY_TYPE_FIELD.equals(entry.field());
    boolean plainString =
        NAME_FIELD.equals(entry.field()) || DESCRIPTION_FIELD.equals(entry.field()) || entityType;
    if (!TEMPLATE_ENTITY.equals(entry.entity())
        || !(entry.to() instanceof String to)
        || !(hashValued || plainString)
        || hashValued != to.startsWith(PackReconcileEntry.HASH_PREFIX)) {
      log.warn(
          "Reconcile entry ignored: template pack {} unsupported entity/field/value '{}'.{} (key"
              + " {}) — only document_template content / requiredContextFields with a sha256:"
              + " target, or name / description / primaryEntityType with a plain string target,"
              + " is supported",
          pack.packId(),
          entry.entity(),
          entry.field(),
          entry.key());
      return Optional.empty();
    }
    var templateDef =
        pack.templates().stream().filter(t -> entry.key().equals(t.templateKey())).findFirst();
    if (templateDef.isEmpty()) {
      log.warn(
          "Reconcile entry ignored: template pack {} has no template '{}'",
          pack.packId(),
          entry.key());
      return Optional.empty();
    }
    if (requiredFields) {
      List<Map<String, String>> shippedFields = templateDef.get().requiredContextFields();
      if (shippedFields.isEmpty()
          || !to.equals(PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(shippedFields))) {
        log.warn(
            "Reconcile entry ignored: template pack {} '{}' {} 'to' does not match the shipped"
                + " value",
            pack.packId(),
            entry.key(),
            entry.field());
        return Optional.empty();
      }
      Map<String, Object> shippedBody =
          loadTemplateContent(templateDef.get(), packResource).content();
      return packSeededRow(pack, entry)
          .map(template -> requiredContextFieldsTarget(template, shippedBody, shippedFields));
    }
    if (entityType) {
      if (!to.equals(templateDef.get().primaryEntityType())) {
        log.warn(
            "Reconcile entry ignored: template pack {} '{}' {} 'to' does not match the shipped"
                + " value",
            pack.packId(),
            entry.key(),
            entry.field());
        return Optional.empty();
      }
      Map<String, Object> shippedBody =
          loadTemplateContent(templateDef.get(), packResource).content();
      return packSeededRow(pack, entry).map(template -> entityTypeTarget(template, shippedBody));
    }
    if (plainString) {
      String shippedValue =
          NAME_FIELD.equals(entry.field())
              ? templateDef.get().name()
              : templateDef.get().description();
      if (!to.equals(shippedValue)) {
        log.warn(
            "Reconcile entry ignored: template pack {} '{}' {} 'to' does not match the shipped"
                + " value",
            pack.packId(),
            entry.key(),
            entry.field());
        return Optional.empty();
      }
      return packSeededRow(pack, entry).map(template -> nameOrDescriptionTarget(template, entry));
    }
    Map<String, Object> shipped = loadTemplateContent(templateDef.get(), packResource).content();
    if (!to.equals(PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(shipped))) {
      log.warn(
          "Reconcile entry ignored: template pack {} '{}' to-hash does not match the shipped body",
          pack.packId(),
          entry.key());
      return Optional.empty();
    }
    return packSeededRow(pack, entry)
        .map(
            template ->
                new PackReconcileEntry.Target() {
                  @Override
                  public Object currentValue() {
                    return template.getContent();
                  }

                  @Override
                  public void apply(Object toHash) {
                    // sha256: contract — the argument is the hash string; write the shipped body.
                    template.updateContent(
                        template.getName(), template.getDescription(), shipped, template.getCss());
                  }
                });
  }

  /**
   * {@code primaryEntityType}: the row's entity type name while its body is the shipped body, else
   * no current value (a tenant-edited body keeps its entity type — LEFT_ALONE). Written through the
   * managed entity.
   */
  private static PackReconcileEntry.Target entityTypeTarget(
      DocumentTemplate template, Map<String, Object> shippedBody) {
    String shippedHash = ContentHashUtil.hashOf(shippedBody);
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return shippedHash.equals(ContentHashUtil.hashOf(template.getContent()))
            ? template.getPrimaryEntityType().name()
            : null;
      }

      @Override
      public void apply(Object to) {
        template.changePrimaryEntityType(TemplateEntityType.valueOf((String) to));
      }
    };
  }

  /**
   * {@code requiredContextFields} (Phase 89 C1): the row's declared list (null when none) while its
   * body is the shipped body, else a sentinel so a tenant-edited row is LEFT_ALONE. {@code apply}
   * ignores the hash argument and writes a copy of the shipped list through the managed entity.
   */
  private static PackReconcileEntry.Target requiredContextFieldsTarget(
      DocumentTemplate template,
      Map<String, Object> shippedBody,
      List<Map<String, String>> shippedFields) {
    String shippedHash = ContentHashUtil.hashOf(shippedBody);
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        if (!shippedHash.equals(ContentHashUtil.hashOf(template.getContent()))) {
          return TENANT_EDITED_BODY;
        }
        var current = template.getRequiredContextFields();
        return current == null || current.isEmpty() ? null : current;
      }

      @Override
      public void apply(Object toHash) {
        template.setRequiredContextFields(mutableCopyOf(shippedFields));
      }
    };
  }

  /** Mutable copies for the JSON column — never share the pack record's immutable maps. */
  private static List<Map<String, String>> mutableCopyOf(List<Map<String, String>> fields) {
    var copy = new ArrayList<Map<String, String>>(fields.size());
    for (Map<String, String> field : fields) {
      copy.add(new LinkedHashMap<>(field));
    }
    return copy;
  }

  /** {@code name} / {@code description}: string target written through the managed entity. */
  private static PackReconcileEntry.Target nameOrDescriptionTarget(
      DocumentTemplate template, PackReconcileEntry entry) {
    boolean name = NAME_FIELD.equals(entry.field());
    return new PackReconcileEntry.Target() {
      @Override
      public Object currentValue() {
        return name ? template.getName() : template.getDescription();
      }

      @Override
      public void apply(Object to) {
        template.updateContent(
            name ? (String) to : template.getName(),
            name ? template.getDescription() : (String) to,
            template.getContent(),
            template.getCss());
      }
    };
  }

  /**
   * The single clone-safe pack-seeded row for the entry's template key in this pack's install
   * (empty when there is none; WARN + empty when more than one).
   */
  private Optional<DocumentTemplate> packSeededRow(
      TemplatePackDefinition pack, PackReconcileEntry entry) {
    UUID installId =
        packInstallRepository.findByPackId(pack.packId()).map(PackInstall::getId).orElse(null);
    if (installId == null) {
      return Optional.empty();
    }
    List<DocumentTemplate> rows =
        documentTemplateRepository.findPackSeededByInstall(installId, entry.key());
    if (rows.size() > 1) {
      log.warn(
          "Reconcile entry ignored: template pack {} '{}' is ambiguous ({} pack-seeded rows)",
          pack.packId(),
          entry.key(),
          rows.size());
      return Optional.empty();
    }
    return rows.stream().findFirst();
  }

  private void createTemplate(
      TemplatePackDefinition pack, TemplatePackTemplate templateDef, Resource packResource) {
    TemplateCategory category = TemplateCategory.valueOf(templateDef.category());
    TemplateEntityType entityType = TemplateEntityType.valueOf(templateDef.primaryEntityType());

    TemplateContent templateContent = loadTemplateContent(templateDef, packResource);
    String css = templateContent.css();
    Map<String, Object> contentJson = templateContent.content();

    String slug = DocumentTemplate.generateSlug(templateDef.name());

    var dt = new DocumentTemplate(entityType, templateDef.name(), slug, category, contentJson);
    dt.setDescription(templateDef.description());
    dt.setCss(css);
    dt.setSource(TemplateSource.PLATFORM);
    dt.setPackId(pack.packId());
    dt.setPackTemplateKey(templateDef.templateKey());
    dt.setSortOrder(templateDef.sortOrder());
    dt.setAcceptanceEligible(Boolean.TRUE.equals(templateDef.acceptanceEligible()));
    if (!templateDef.requiredContextFields().isEmpty()) {
      dt.setRequiredContextFields(mutableCopyOf(templateDef.requiredContextFields()));
    }

    documentTemplateRepository.save(dt);
  }

  private String loadTemplateContentAsString(Resource packJsonResource, String filename) {
    try {
      Resource contentResource = packJsonResource.createRelative(filename);
      return contentResource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load template content file: " + filename, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadTemplateContentAsJson(
      Resource packJsonResource, String filename) {
    try {
      Resource contentResource = packJsonResource.createRelative(filename);
      return objectMapper().readValue(contentResource.getInputStream(), Map.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse template content JSON file: " + filename, e);
    }
  }

  /**
   * Returns all available template packs loaded from the classpath. Used by {@code
   * TemplatePackInstaller} to build the pack catalog.
   */
  public List<LoadedPack<TemplatePackDefinition>> getAvailablePacks() {
    return loadPacks();
  }

  /**
   * Applies a single pack's content (creates DocumentTemplate rows). Public delegate for {@link
   * #applyPack} to allow cross-package access from {@code TemplatePackInstaller}.
   */
  public void applyPackContent(
      TemplatePackDefinition pack, Resource packResource, String tenantId) {
    applyPack(pack, packResource, tenantId);
  }
}
