package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
        .containsExactlyInAnyOrder("court_calendar", "conflict_check", "lssa_tariff");
  }

  @Test
  void legalZaProfileDoesNotIncludeTrustAccounting() {
    var profile = registry.getProfile("legal-za");

    assertThat(profile).isPresent();
    assertThat(profile.get().enabledModules()).doesNotContain("trust_accounting");
  }
}
