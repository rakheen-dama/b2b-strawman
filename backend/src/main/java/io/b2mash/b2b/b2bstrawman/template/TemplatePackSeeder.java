package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

  private final DocumentTemplateRepository documentTemplateRepository;

  public TemplatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.documentTemplateRepository = documentTemplateRepository;
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
    if (settings.getTemplatePackStatus() == null) {
      return false;
    }
    return settings.getTemplatePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, TemplatePackDefinition pack) {
    settings.recordTemplatePackApplication(pack.packId(), pack.version());
  }

  @Override
  protected void applyPack(TemplatePackDefinition pack, Resource packResource, String tenantId) {
    for (TemplatePackTemplate templateDef : pack.templates()) {
      TemplateCategory category = TemplateCategory.valueOf(templateDef.category());
      TemplateEntityType entityType = TemplateEntityType.valueOf(templateDef.primaryEntityType());

      String css =
          templateDef.cssFile() != null
              ? loadTemplateContentAsString(packResource, templateDef.cssFile())
              : null;

      String slug = DocumentTemplate.generateSlug(templateDef.name());

      // Load content as JSONB Map for the primary content field
      Map<String, Object> contentJson = null;
      if (templateDef.contentFile() != null && templateDef.contentFile().endsWith(".json")) {
        contentJson = loadTemplateContentAsJson(packResource, templateDef.contentFile());
      }
      if (contentJson == null) {
        throw new IllegalStateException(
            "Template definition has no JSON content file: " + templateDef.contentFile());
      }

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
