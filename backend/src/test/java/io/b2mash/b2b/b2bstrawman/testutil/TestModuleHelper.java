package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.Arrays;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared test utility for enabling horizontal modules in test setups. Many services are gated
 * behind {@link io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard}; tests that exercise those
 * services must enable the relevant modules first.
 *
 * <p>All methods are static — pass the test's {@link MockMvc} instance as the first argument.
 */
public final class TestModuleHelper {

  private TestModuleHelper() {}

  /**
   * Replaces the set of enabled horizontal modules for the given tenant. Requires an owner/admin
   * member to already exist in the tenant (use {@link TestMemberHelper} first).
   *
   * <p>Module IDs are strings like {@code "resource_planning"}, {@code "bulk_billing"}, {@code
   * "automation_builder"}. Passing an empty array clears all horizontal modules.
   */
  public static void enableModules(
      MockMvc mockMvc, String orgId, String ownerSubject, String... moduleIds) throws Exception {
    StringBuilder json = new StringBuilder("{\"enabledModules\": [");
    for (int i = 0; i < moduleIds.length; i++) {
      if (i > 0) json.append(",");
      json.append("\"").append(moduleIds[i]).append("\"");
    }
    json.append("]}");

    mockMvc
        .perform(
            put("/api/settings/modules")
                .with(TestJwtFactory.ownerJwt(orgId, ownerSubject))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.toString()))
        .andExpect(status().isOk());
  }

  /**
   * Enables modules directly via the settings service + repository. Use when the test doesn't have
   * MockMvc set up or calls services directly inside a {@code ScopedValue.where(...).run(...)}
   * block. Caller must already be inside the tenant scope.
   */
  public static void enableModulesInTenant(
      OrgSettingsService orgSettingsService,
      OrgSettingsRepository orgSettingsRepository,
      String... moduleIds) {
    var settings = orgSettingsService.getOrCreateForCurrentTenant();
    settings.setEnabledModules(Arrays.asList(moduleIds));
    orgSettingsRepository.save(settings);
  }
}
