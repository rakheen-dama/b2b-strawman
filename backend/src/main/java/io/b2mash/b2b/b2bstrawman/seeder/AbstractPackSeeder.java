package io.b2mash.b2b.b2bstrawman.seeder;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for pack seeders that load JSON pack definitions from the classpath and seed
 * entity-specific data for tenants. Handles the common flow of classpath scanning, JSON
 * deserialization, OrgSettings tracking (idempotency), and tenant transaction scoping.
 *
 * <p>Subclasses implement the template methods to specify:
 *
 * <ul>
 *   <li>Where to find pack files on the classpath
 *   <li>How to deserialize JSON into a pack definition type
 *   <li>How to track applied packs in OrgSettings (read and write)
 *   <li>How to create domain entities from a pack definition
 * </ul>
 *
 * @param <D> the pack definition type (e.g., TemplatePackDefinition, ClausePackDefinition)
 */
public abstract class AbstractPackSeeder<D> {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final ResourcePatternResolver resourceResolver;
  private final ObjectMapper objectMapper;
  private final OrgSettingsRepository orgSettingsRepository;
  private final TenantTransactionHelper tenantTransactionHelper;

  protected AbstractPackSeeder(
      ResourcePatternResolver resourceResolver,
      ObjectMapper objectMapper,
      OrgSettingsRepository orgSettingsRepository,
      TenantTransactionHelper tenantTransactionHelper) {
    this.resourceResolver = resourceResolver;
    this.objectMapper = objectMapper;
    this.orgSettingsRepository = orgSettingsRepository;
    this.tenantTransactionHelper = tenantTransactionHelper;
  }

  /** Classpath resource pattern for pack JSON files (e.g., "classpath:field-packs/*.json"). */
  protected abstract String getPackResourcePattern();

  /** The Java class to deserialize each pack JSON file into. */
  protected abstract Class<D> getPackDefinitionType();

  /** Human-readable name for this pack type, used in log messages (e.g., "field", "template"). */
  protected abstract String getPackTypeName();

  /** Extract the pack identifier from a deserialized pack definition. */
  protected abstract String getPackId(D pack);

  /** Extract the pack version from a deserialized pack definition (as a string for display). */
  protected abstract String getPackVersion(D pack);

  /** Check whether a pack has already been applied, using the OrgSettings status list. */
  protected abstract boolean isPackAlreadyApplied(OrgSettings settings, String packId);

  /** Record a successful pack application in OrgSettings. */
  protected abstract void recordPackApplication(OrgSettings settings, D pack);

  /**
   * Apply the pack definition to create domain entities. Called once per pack that hasn't been
   * applied yet.
   *
   * @param pack the deserialized pack definition
   * @param packResource the classpath Resource the pack was loaded from (useful for loading
   *     relative files)
   * @param tenantId the tenant schema name
   */
  protected abstract void applyPack(D pack, Resource packResource, String tenantId);

  /**
   * Reconcile settings on an already-applied pack. Called during reconciliation when a pack has
   * already been seeded but its JSON definition may have changed (e.g., autoInstantiate flag
   * toggled). Default is a no-op; subclasses override to sync specific fields.
   *
   * @param pack the current pack definition from the classpath
   * @param tenantId the tenant schema name
   */
  protected void reconcileExistingPack(D pack, String tenantId) {
    // Default: no-op — subclasses override to sync mutable pack settings
  }

  /**
   * Extract the vertical profile from a deserialized pack definition. Returns null for universal
   * packs that apply to all tenants.
   */
  protected abstract String getVerticalProfile(D pack);

  /**
   * Seeds all available packs for the given tenant. Entry point called by provisioning and
   * reconciliation code. Delegates to {@link TenantTransactionHelper} to run within the correct
   * tenant transaction scope.
   */
  public void seedPacksForTenant(String tenantId, String orgId) {
    tenantTransactionHelper.executeInTenantTransaction(tenantId, orgId, t -> doSeedPacks(t));
  }

  private void doSeedPacks(String tenantId) {
    List<LoadedPack<D>> packs = loadPacks();
    if (packs.isEmpty()) {
      log.info("No {} packs found on classpath for tenant {}", getPackTypeName(), tenantId);
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

    for (LoadedPack<D> loaded : packs) {
      D pack = loaded.definition();
      String packId = getPackId(pack);

      // Vertical profile filtering: skip packs targeting a different vertical
      String tenantProfile = settings.getVerticalProfile();
      String packProfile = getVerticalProfile(pack);
      if (packProfile != null && !packProfile.equals(tenantProfile)) {
        log.debug(
            "Skipping {} pack {} (profile {} != tenant profile {})",
            getPackTypeName(),
            packId,
            packProfile,
            tenantProfile);
        continue;
      }

      if (isPackAlreadyApplied(settings, packId)) {
        log.info(
            "{} pack {} already applied for tenant {}, reconciling settings",
            capitalize(getPackTypeName()),
            packId,
            tenantId);
        reconcileExistingPack(pack, tenantId);
        continue;
      }

      applyPack(pack, loaded.resource(), tenantId);
      recordPackApplication(settings, pack);
      log.info(
          "Applied {} pack {} v{} for tenant {}",
          getPackTypeName(),
          packId,
          getPackVersion(pack),
          tenantId);
    }

    orgSettingsRepository.save(settings);
  }

  protected List<LoadedPack<D>> loadPacks() {
    try {
      Resource[] resources = resourceResolver.getResources(getPackResourcePattern());
      return Arrays.stream(resources)
          .map(
              resource -> {
                try {
                  var definition =
                      objectMapper.readValue(resource.getInputStream(), getPackDefinitionType());
                  return new LoadedPack<>(definition, resource);
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Failed to parse " + getPackTypeName() + " pack: " + resource.getFilename(),
                      e);
                }
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to scan for {} packs at {}", getPackTypeName(), getPackResourcePattern(), e);
      return List.of();
    }
  }

  /** Provides access to the ObjectMapper for subclasses that need additional JSON parsing. */
  protected ObjectMapper objectMapper() {
    return objectMapper;
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * A pack definition paired with the classpath Resource it was loaded from. Subclasses that need
   * to load relative files (e.g., CSS or content JSON alongside pack.json) can use the resource.
   */
  public record LoadedPack<D>(D definition, Resource resource) {}
}
