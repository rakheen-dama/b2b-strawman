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

  /** ADR-356 reconcile entity name for template bodies (the only supported field: content). */
  private static final String TEMPLATE_ENTITY = "document_template";

  private static final String CONTENT_FIELD = "content";

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
   * ADR-356 reconcile target for a {@code document_template} {@code content} entry (Phase 88 626D):
   * {@code key} is the pack's {@code templateKey} ({@code pack_template_key}) and {@code to} is
   * {@code sha256:} + {@link ContentHashUtil#hashOf} of the body shipped in this pack. The target
   * is this pack install's pack-seeded row only — {@code PLATFORM}, not a clone, tagged with this
   * pack's {@code pack_install} id — so clones and firm-authored templates are never written; the
   * one rule (current body hash = {@code from}) keeps firm edits. Writes the classpath body through
   * the managed entity (css untouched) and never a content hash — {@code TemplatePackInstaller}
   * re-pins pristine rows. Unsupported entries, a {@code to} that does not match the shipped body
   * and ambiguous rows are WARN-logged and skipped; nothing here throws at boot.
   */
  @Override
  protected Optional<PackReconcileEntry.Target> resolveReconcileTarget(
      TemplatePackDefinition pack, PackReconcileEntry entry, Resource packResource) {
    if (!TEMPLATE_ENTITY.equals(entry.entity())
        || !CONTENT_FIELD.equals(entry.field())
        || !(entry.to() instanceof String to)
        || !to.startsWith(PackReconcileEntry.HASH_PREFIX)) {
      log.warn(
          "Reconcile entry ignored: template pack {} unsupported entity/field/value '{}'.{} (key"
              + " {}) — only document_template content with a sha256: target is supported",
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
    Map<String, Object> shipped = loadTemplateContent(templateDef.get(), packResource).content();
    if (!to.equals(PackReconcileEntry.HASH_PREFIX + ContentHashUtil.hashOf(shipped))) {
      log.warn(
          "Reconcile entry ignored: template pack {} '{}' to-hash does not match the shipped body",
          pack.packId(),
          entry.key());
      return Optional.empty();
    }
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
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    DocumentTemplate template = rows.getFirst();
    return Optional.of(
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
