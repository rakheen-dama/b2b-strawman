package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * Seeds template packs (JSON-defined document templates) for newly provisioned tenants. Reads pack
 * files from classpath:template-packs/&#42;/pack.json. Idempotent: tracks applied packs in
 * OrgSettings.templatePackStatus.
 */
@Service
public class TemplatePackSeeder {

  private static final Logger log = LoggerFactory.getLogger(TemplatePackSeeder.class);
  private static final String PACK_LOCATION = "classpath:template-packs/*/pack.json";

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final DocumentTemplateRepository documentTemplateRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TransactionTemplate transactionTemplate;

  public TemplatePackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      DocumentTemplateRepository documentTemplateRepository,
      OrgSettingsRepository orgSettingsRepository,
      TransactionTemplate transactionTemplate) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.documentTemplateRepository = documentTemplateRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Seeds all available template packs for the given tenant. Must be called during or after tenant
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
    List<PackWithResource> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No template packs found on classpath for tenant {}", tenantId);
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

    for (PackWithResource packEntry : packs) {
      TemplatePackDefinition pack = packEntry.definition();
      if (isPackAlreadyApplied(settings, pack.packId())) {
        log.info("Pack {} already applied for tenant {}, skipping", pack.packId(), tenantId);
        continue;
      }

      applyPack(pack, packEntry.packResource());
      settings.recordTemplatePackApplication(pack.packId(), pack.version());
      log.info(
          "Applied template pack {} v{} for tenant {}", pack.packId(), pack.version(), tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  private List<PackWithResource> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(PACK_LOCATION);
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  var definition =
                      objectMapper.readValue(
                          resource.getInputStream(), TemplatePackDefinition.class);
                  return new PackWithResource(definition, resource);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse template pack: " + resource.getFilename(), e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for template packs at {}", PACK_LOCATION, e);
      return List.of();
    }
  }

  private boolean isPackAlreadyApplied(OrgSettings settings, String packId) {
    if (settings.getTemplatePackStatus() == null) {
      return false;
    }
    return settings.getTemplatePackStatus().stream()
        .anyMatch(entry -> packId.equals(entry.get("packId")));
  }

  private void applyPack(TemplatePackDefinition pack, Resource packJsonResource) {
    for (TemplatePackTemplate templateDef : pack.templates()) {
      TemplateCategory category = TemplateCategory.valueOf(templateDef.category());
      TemplateEntityType entityType = TemplateEntityType.valueOf(templateDef.primaryEntityType());

      String css =
          templateDef.cssFile() != null
              ? loadTemplateContentAsString(packJsonResource, templateDef.cssFile())
              : null;

      String slug = DocumentTemplate.generateSlug(templateDef.name());

      // Load content as JSONB Map for the primary content field
      Map<String, Object> contentJson = null;
      if (templateDef.contentFile() != null && templateDef.contentFile().endsWith(".json")) {
        contentJson = loadTemplateContentAsJson(packJsonResource, templateDef.contentFile());
      }

      var dt = new DocumentTemplate(entityType, templateDef.name(), slug, category, contentJson);
      dt.setDescription(templateDef.description());
      dt.setCss(css);
      dt.setSource(TemplateSource.PLATFORM);
      dt.setPackId(pack.packId());
      dt.setPackTemplateKey(templateDef.templateKey());
      dt.setSortOrder(templateDef.sortOrder());

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
      return objectMapper.readValue(contentResource.getInputStream(), Map.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse template content JSON file: " + filename, e);
    }
  }

  private record PackWithResource(TemplatePackDefinition definition, Resource packResource) {}
}
