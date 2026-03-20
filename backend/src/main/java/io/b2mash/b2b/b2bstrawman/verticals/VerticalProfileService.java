package io.b2mash.b2b.b2bstrawman.verticals;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry.ModuleDefinition;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service that assembles profile and module responses for the vertical profile controller. */
@Service
public class VerticalProfileService {

  private final VerticalProfileRegistry profileRegistry;
  private final VerticalModuleRegistry moduleRegistry;
  private final OrgSettingsService orgSettingsService;

  public VerticalProfileService(
      VerticalProfileRegistry profileRegistry,
      VerticalModuleRegistry moduleRegistry,
      OrgSettingsService orgSettingsService) {
    this.profileRegistry = profileRegistry;
    this.moduleRegistry = moduleRegistry;
    this.orgSettingsService = orgSettingsService;
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
}
