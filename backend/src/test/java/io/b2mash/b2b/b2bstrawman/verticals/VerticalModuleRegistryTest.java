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
  void getAllModules_returnsThreeModulesWithCorrectIds() {
    var modules = registry.getAllModules();

    assertThat(modules).hasSize(3);
    assertThat(modules)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder("trust_accounting", "court_calendar", "conflict_check");
  }

  @Test
  void getModule_returnsCorrectMetadataForTrustAccounting() {
    var module = registry.getModule("trust_accounting");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Trust Accounting");
    assertThat(module.get().description())
        .isEqualTo("LSSA-compliant trust account management for client funds");
    assertThat(module.get().status()).isEqualTo("stub");
  }

  @Test
  void getModule_returnsEmptyForNonexistent() {
    assertThat(registry.getModule("nonexistent")).isEmpty();
  }
}
