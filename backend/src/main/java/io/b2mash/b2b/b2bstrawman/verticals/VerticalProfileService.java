package io.b2mash.b2b.b2bstrawman.verticals;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry.ModuleDefinition;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service that assembles profile and module responses for the vertical profile controller. */
@Service
public class VerticalProfileService {

  private static final Logger log = LoggerFactory.getLogger(VerticalProfileService.class);

  private final VerticalProfileRegistry profileRegistry;
  private final VerticalModuleRegistry moduleRegistry;
  private final OrgSettingsService orgSettingsService;
  private final OrgSettingsRepository orgSettingsRepository;
  private final RatePackSeeder ratePackSeeder;
  private final SchedulePackSeeder schedulePackSeeder;

  public VerticalProfileService(
      VerticalProfileRegistry profileRegistry,
      VerticalModuleRegistry moduleRegistry,
      OrgSettingsService orgSettingsService,
      OrgSettingsRepository orgSettingsRepository,
      RatePackSeeder ratePackSeeder,
      SchedulePackSeeder schedulePackSeeder) {
    this.profileRegistry = profileRegistry;
    this.moduleRegistry = moduleRegistry;
    this.orgSettingsService = orgSettingsService;
    this.orgSettingsRepository = orgSettingsRepository;
    this.ratePackSeeder = ratePackSeeder;
    this.schedulePackSeeder = schedulePackSeeder;
  }

  /** DTO for profile summary. */
  public record ProfileSummary(String id, String name, String description, List<String> modules) {}

  /** DTO for module with enabled status. */
  public record ModuleWithStatus(
      String id, String name, String description, boolean enabled, String status) {}

  /** Returns summaries of all available vertical profiles. */
  public List<ProfileSummary> getProfileSummaries() {
    return profileRegistry.getAllProfiles().stream()
        .map(p -> new ProfileSummary(p.profileId(), p.name(), p.description(), p.enabledModules()))
        .toList();
  }

  /**
   * Returns all known modules with their enabled status for the current tenant. Cross-references
   * the module registry with the tenant's enabled_modules from OrgSettings.
   */
  @Transactional(readOnly = true)
  public List<ModuleWithStatus> getModulesWithStatus() {
    Set<String> enabledSet = Set.copyOf(orgSettingsService.getEnabledModulesForCurrentTenant());
    return moduleRegistry.getAllModules().stream()
        .map(
            (ModuleDefinition m) ->
                new ModuleWithStatus(
                    m.id(), m.name(), m.description(), enabledSet.contains(m.id()), m.status()))
        .toList();
  }

  /**
   * Switches the current tenant to a new vertical profile. Updates org settings (profile, enabled
   * modules, terminology) and triggers rate and schedule pack seeders for the new profile. Seeding
   * is idempotent — calling this method multiple times with the same profile is safe.
   *
   * <p>Requires a bound tenant context (RequestScopes.TENANT_ID and ORG_ID must be set).
   */
  @Transactional
  public void switchProfile(String newProfile) {
    // Update org settings with the new profile, modules, and terminology
    var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
    String oldProfile = settings.getVerticalProfile();

    var profileDef = profileRegistry.getProfile(newProfile);
    if (profileDef.isPresent()) {
      var profile = profileDef.get();
      settings.setVerticalProfile(newProfile);
      settings.setEnabledModules(profile.enabledModules());
      settings.setTerminologyNamespace(profile.terminologyNamespace());
      if (profile.currency() != null) {
        settings.updateCurrency(profile.currency());
      }
    } else {
      log.warn("Vertical profile '{}' not found in registry, setting profile only", newProfile);
      settings.setVerticalProfile(newProfile);
    }
    orgSettingsRepository.save(settings);

    // Trigger rate and schedule pack seeding for the new profile (idempotent)
    String tenantId = RequestScopes.TENANT_ID.get();
    String orgId = RequestScopes.ORG_ID.get();
    ratePackSeeder.seedPacksForTenant(tenantId, orgId);
    schedulePackSeeder.seedPacksForTenant(tenantId, orgId);

    log.info("Switched vertical profile from '{}' to '{}'", oldProfile, newProfile);
  }
}
