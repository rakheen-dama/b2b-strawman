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
    assertThat(p.enabledModules()).isEmpty();
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
            "court_calendar", "conflict_check", "lssa_tariff", "trust_accounting");
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
