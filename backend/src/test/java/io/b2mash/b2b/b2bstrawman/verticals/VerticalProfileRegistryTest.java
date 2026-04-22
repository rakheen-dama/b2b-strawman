package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class VerticalProfileRegistryTest {

  private VerticalProfileRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    registry = new VerticalProfileRegistry(new ObjectMapper());
  }

  @Test
  void loadsAccountingZaProfileWithCorrectFields() {
    var profile = registry.getProfile("accounting-za");

    assertThat(profile).isPresent();

    var p = profile.get();
    assertThat(p.profileId()).isEqualTo("accounting-za");
    assertThat(p.name()).isEqualTo("South African Accounting Firm");
    assertThat(p.description()).startsWith("Complete configuration for");
    assertThat(p.currency()).isEqualTo("ZAR");
    assertThat(p.enabledModules()).containsExactlyInAnyOrder("deadlines", "information_requests");
    assertThat(p.terminologyNamespace()).isEqualTo("en-ZA-accounting");
    assertThat(p.packs()).containsKey("field");
  }

  @Test
  void existsReturnsFalseForNonexistent() {
    assertThat(registry.exists("nonexistent")).isFalse();
  }

  @Test
  void existsReturnsTrueForAccountingZa() {
    assertThat(registry.exists("accounting-za")).isTrue();
  }

  @Test
  void getAllProfilesReturnsLoadedProfiles() {
    var profiles = registry.getAllProfiles();

    assertThat(profiles).isNotEmpty();
    assertThat(profiles)
        .extracting(VerticalProfileRegistry.ProfileDefinition::profileId)
        .contains("accounting-za");
  }

  @Test
  void legalZaProfileIncludesLegalModules() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();

    var p = profile.get();
    assertThat(p.enabledModules())
        .containsExactlyInAnyOrder(
            "court_calendar",
            "conflict_check",
            "lssa_tariff",
            "trust_accounting",
            "disbursements",
            "matter_closure",
            "deadlines",
            "information_requests");
  }

  @Test
  void consultingZaProfileEnablesResourcePlanningAndAutomationBuilder() {
    // GAP-C-04 + GAP-C-07: TeamUtilizationWidget depends on resource_planning and the
    // Automations UI depends on automation_builder. Both modules ship enabled for consulting-za
    // so fresh tenants can see utilization and manage automation rules out of the box.
    // GAP-P-01 also enables information_requests for portal request flows.
    var profile = registry.getProfile("consulting-za").orElseThrow();
    assertThat(profile.enabledModules())
        .containsExactlyInAnyOrder(
            "resource_planning", "automation_builder", "information_requests");
  }

  @Test
  void accountingZaProfileOnlyEnablesDeadlinesModule() {
    // Epic 497A added the portal deadline feature; it is enabled for accounting-za. GAP-P-01
    // also enables information_requests. The regression guard still holds for every OTHER
    // module — none of the GAP-C-04/C-07 modules may leak into accounting-za.
    var profile = registry.getProfile("accounting-za").orElseThrow();
    assertThat(profile.enabledModules())
        .containsExactlyInAnyOrder("deadlines", "information_requests");
  }

  @Test
  void consultingGenericProfileKeepsEnabledModulesEmpty() {
    // Regression guard for GAP-C-04/C-07: the fix must stay scoped to consulting-za.
    var profile = registry.getProfile("consulting-generic").orElseThrow();
    assertThat(profile.enabledModules()).isEmpty();
  }

  @Test
  void consultingZaProfileExposesRateCardDefaults() {
    var profile = registry.getProfile("consulting-za").orElseThrow();
    var defaults = profile.rateCardDefaults();

    assertThat(defaults).as("consulting-za must expose rateCardDefaults").isNotNull();
    assertThat(defaults.currency()).isEqualTo("ZAR");

    assertThat(defaults.billingRates()).hasSize(3);
    assertThat(defaults.billingRates())
        .extracting(VerticalProfileRegistry.RoleRate::roleName)
        .containsExactlyInAnyOrder("Owner", "Admin", "Member");
    assertThat(defaults.billingRates())
        .filteredOn(r -> "Owner".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("1800"));
    assertThat(defaults.billingRates())
        .filteredOn(r -> "Admin".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("1200"));
    assertThat(defaults.billingRates())
        .filteredOn(r -> "Member".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("750"));

    assertThat(defaults.costRates()).hasSize(3);
    assertThat(defaults.costRates())
        .filteredOn(r -> "Owner".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("850"));
    assertThat(defaults.costRates())
        .filteredOn(r -> "Admin".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("550"));
    assertThat(defaults.costRates())
        .filteredOn(r -> "Member".equals(r.roleName()))
        .extracting(VerticalProfileRegistry.RoleRate::hourlyRate)
        .containsExactly(new BigDecimal("375"));
  }

  @Test
  void accountingZaProfileExposesRateCardDefaults() {
    var profile = registry.getProfile("accounting-za").orElseThrow();
    var defaults = profile.rateCardDefaults();

    assertThat(defaults).isNotNull();
    assertThat(defaults.currency()).isEqualTo("ZAR");
    assertThat(defaults.billingRates()).hasSize(3);
    assertThat(defaults.costRates()).hasSize(3);
  }

  @Test
  void legalZaProfileHasNoRateCardDefaults() {
    var profile = registry.getProfile("legal-za").orElseThrow();
    assertThat(profile.rateCardDefaults()).isNull();
  }
}
