package io.b2mash.b2b.b2bstrawman.verticals;

import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VerticalModuleGuard {

  private final OrgSettingsService orgSettingsService;

  public VerticalModuleGuard(OrgSettingsService orgSettingsService) {
    this.orgSettingsService = orgSettingsService;
  }

  /**
   * Throws ModuleNotEnabledException if the given module is not in the current tenant's
   * enabled_modules list.
   */
  public void requireModule(String moduleId) {
    if (!isModuleEnabled(moduleId)) {
      throw new ModuleNotEnabledException(moduleId);
    }
  }

  /** Returns true if the given module is in the current tenant's enabled_modules list. */
  public boolean isModuleEnabled(String moduleId) {
    return getEnabledModules().contains(moduleId);
  }

  /**
   * Returns the set of enabled module IDs for the current tenant. Cached per-request via
   * OrgSettingsService (Hibernate L1 cache).
   */
  public Set<String> getEnabledModules() {
    return Set.copyOf(orgSettingsService.getEnabledModulesForCurrentTenant());
  }
}
