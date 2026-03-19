package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackDefinition;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackTemplate;
import io.b2mash.b2b.b2bstrawman.template.TemplateSource;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Seeds PAIA Section 51 manual document template for South African jurisdictions. Extends {@link
 * AbstractPackSeeder} using {@link TemplatePackDefinition} and tracks applied packs in {@code
 * compliancePackStatus} (separate from regular template pack tracking).
 *
 * <p>Named {@code ComplianceTemplatePackSeeder} to avoid conflicting with the existing {@code
 * compliance.CompliancePackSeeder} which seeds checklist/field packs.
 */
@Service
public class ComplianceTemplatePackSeeder extends AbstractPackSeeder<TemplatePackDefinition> {

  private static final String PACK_LOCATION = "classpath:template-packs/compliance-za/pack.json";

  private final DocumentTemplateRepository documentTemplateRepository;

  public ComplianceTemplatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    super(resourceResolver, objectMapper, orgSettingsRepository, tenantTransactionHelper);
    this.documentTemplateRepository = documentTemplateRepository;
  }

  /**
   * Seeds the compliance template pack for the given jurisdiction. Only seeds for ZA (South Africa)
   * jurisdiction. Safe to call from within a tenant transaction context.
   */
  public void seedCompliancePack(String jurisdiction) {
    if (!"ZA".equals(jurisdiction)) {
      return;
    }
    String tenantId = RequestScopes.requireTenantId();
    String orgId = RequestScopes.requireOrgId();
    seedPacksForTenant(tenantId, orgId);
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
    return "compliance-template";
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
    if (settings.getCompliancePackStatus() == null) {
      return false;
    }
    return settings.getCompliancePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  @Override
  protected void recordPackApplication(OrgSettings settings, TemplatePackDefinition pack) {
    settings.recordCompliancePackApplication(pack.packId(), String.valueOf(pack.version()));
  }

  @Override
  protected void applyPack(TemplatePackDefinition pack, Resource packResource, String tenantId) {
    for (TemplatePackTemplate templateDef : pack.templates()) {
      String slug = DocumentTemplate.generateSlug(templateDef.name());

      // Skip if template already exists (may have been seeded by TemplatePackSeeder)
      if (documentTemplateRepository.findBySlug(slug).isPresent()) {
        log.info("Compliance template '{}' already exists for tenant {}, skipping", slug, tenantId);
        continue;
      }

      TemplateCategory category = TemplateCategory.valueOf(templateDef.category());
      TemplateEntityType entityType = TemplateEntityType.valueOf(templateDef.primaryEntityType());
      Map<String, Object> contentJson =
          loadTemplateContentAsJson(packResource, templateDef.contentFile());

      var dt = new DocumentTemplate(entityType, templateDef.name(), slug, category, contentJson);
      dt.setDescription(templateDef.description());
      dt.setSource(TemplateSource.PLATFORM);
      dt.setPackId(pack.packId());
      dt.setPackTemplateKey(templateDef.templateKey());
      dt.setSortOrder(templateDef.sortOrder());
      documentTemplateRepository.save(dt);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadTemplateContentAsJson(
      Resource packJsonResource, String filename) {
    try {
      Resource contentResource = packJsonResource.createRelative(filename);
      return objectMapper().readValue(contentResource.getInputStream(), Map.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to parse compliance template content JSON file: " + filename, e);
    }
  }
}
