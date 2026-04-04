package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerticalModuleRegistryTest {

  private VerticalModuleRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new VerticalModuleRegistry();
  }

  @Test
  void getAllModules_returnsFiveModulesWithCorrectIds() {
    var modules = registry.getAllModules();

    assertThat(modules).hasSize(5);
    assertThat(modules)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
            "regulatory_deadlines",
            "lssa_tariff");
  }

  @Test
  void getModule_returnsCorrectMetadataForTrustAccounting() {
    var module = registry.getModule("trust_accounting");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Trust Accounting");
    assertThat(module.get().description())
        .isEqualTo("LSSA-compliant trust account management for client funds");
    assertThat(module.get().status()).isEqualTo("active");
  }

  @Test
  void getModule_returnsEmptyForNonexistent() {
    assertThat(registry.getModule("nonexistent")).isEmpty();
  }

  @Test
  void getModule_courtCalendarIsActiveWithNavItems() {
    var module = registry.getModule("court_calendar");

    assertThat(module).isPresent();
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/court-calendar");
  }

  @Test
  void getModule_conflictCheckIsActiveWithNavItems() {
    var module = registry.getModule("conflict_check");

    assertThat(module).isPresent();
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/conflict-check");
  }

  @Test
  void getModule_lssaTariffIsActiveWithCorrectNavItems() {
    var module = registry.getModule("lssa_tariff");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("LSSA Tariff");
    assertThat(module.get().description())
        .isEqualTo("LSSA tariff schedule management for legal billing");
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/legal/tariffs");
    assertThat(module.get().navItems().getFirst().label()).isEqualTo("Tariffs");
    assertThat(module.get().navItems().getFirst().zone()).isEqualTo("finance");
  }
}
