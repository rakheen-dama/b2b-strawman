package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalDeadlineService;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleRegistry.ModuleDefinition;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry.ProfileDefinition;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Bug-class validation for {@code vertical-profiles/*.json} {@code enabledModules} slugs.
 *
 * <p>Two source-of-truth-driven invariants protect against the two failure directions:
 *
 * <ul>
 *   <li><b>No unknown slugs</b> — every slug a profile enables must resolve against the union of
 *       (a) {@link VerticalModuleRegistry} module IDs and (b) guard-owned module-ID constants that
 *       live outside the registry (at minimum {@link PortalDeadlineService#MODULE_ID} — the
 *       customer-portal {@code deadlines} module). This catches typos and the original
 *       risk-register B-04 class (a profile referencing a slug no code recognises).
 *   <li><b>Default-enabled modules are actually enabled</b> — every registry module whose {@code
 *       defaultEnabledFor} declares a profile must appear in that profile's {@code enabledModules}.
 *       This catches the inverse defect: the registry declaring intent that the profile JSON never
 *       honours (the {@code regulatory_deadlines} / accounting-za gap that motivated this test).
 * </ul>
 *
 * <p>The valid-slug set is derived from the live code, never hard-coded, so adding a module to the
 * registry or a new guard constant keeps the test honest without edits here.
 */
class VerticalProfileModuleSlugValidationTest {

  private VerticalProfileRegistry profileRegistry;
  private VerticalModuleRegistry moduleRegistry;

  @BeforeEach
  void setUp() throws IOException {
    profileRegistry = new VerticalProfileRegistry(new ObjectMapper());
    moduleRegistry = new VerticalModuleRegistry();
  }

  /**
   * Slugs that are legitimately guarded by application code but intentionally NOT declared as
   * {@link VerticalModuleRegistry} modules. Sourced from the owning class's public constant so the
   * set tracks the code, not a copy. Currently only the customer-portal {@code deadlines} module.
   */
  private Set<String> guardOwnedModuleIds() {
    return Set.of(PortalDeadlineService.MODULE_ID);
  }

  private Set<String> validSlugs() {
    Set<String> valid = new LinkedHashSet<>();
    for (ModuleDefinition module : moduleRegistry.getAllModules()) {
      valid.add(module.id());
    }
    valid.addAll(guardOwnedModuleIds());
    return valid;
  }

  @Test
  void everyEnabledModuleSlugResolvesToAKnownModule() {
    Set<String> valid = validSlugs();

    for (ProfileDefinition profile : profileRegistry.getAllProfiles()) {
      for (String slug : profile.enabledModules()) {
        assertThat(valid)
            .as(
                "profile '%s' enables module '%s' which is not a known registry module nor a"
                    + " guard-owned module constant",
                profile.profileId(), slug)
            .contains(slug);
      }
    }
  }

  /**
   * Known, pre-existing registry/profile divergences that are deliberately NOT seeded into the
   * profile JSON and are tracked for separate product triage — NOT regressions this test should
   * fail on. Each entry is {@code "moduleId@profileId"}.
   *
   * <p>{@code retainer_agreements@legal-za} / {@code @consulting-za}: the module declares {@code
   * defaultEnabledFor} both legal-za and consulting-za, but neither profile JSON seeds it. This is
   * an intentional "Phase 68 module that lives outside the seed" — see {@code
   * PortalContextControllerIntegrationTest} (legal-za setup explicitly adds {@code
   * retainer_agreements} on OrgSettings because the JSON does not). Whether the JSON should seed it
   * (like accounting-za now seeds regulatory_deadlines) is a separate product decision, out of
   * scope for this fix. Remove the relevant entry here when that decision lands.
   */
  private static final Set<String> KNOWN_UNSEEDED_DEFAULTS =
      Set.of("retainer_agreements@legal-za", "retainer_agreements@consulting-za");

  @Test
  void everyRegistryDefaultEnabledModuleIsPresentInItsProfile() {
    for (ModuleDefinition module : moduleRegistry.getAllModules()) {
      for (String profileId : module.defaultEnabledFor()) {
        if (KNOWN_UNSEEDED_DEFAULTS.contains(module.id() + "@" + profileId)) {
          continue;
        }
        ProfileDefinition profile = profileRegistry.getProfile(profileId).orElse(null);
        if (profile == null) {
          // A registry default referencing an unknown profile is itself a defect; surface it.
          assertThat(profile)
              .as(
                  "module '%s' declares defaultEnabledFor profile '%s' which does not exist",
                  module.id(), profileId)
              .isNotNull();
          continue;
        }
        assertThat(profile.enabledModules())
            .as(
                "module '%s' declares defaultEnabledFor '%s' but the profile's enabledModules does"
                    + " not include it — the registry's declared intent is not honoured by the"
                    + " profile JSON",
                module.id(), profileId)
            .contains(module.id());
      }
    }
  }
}
