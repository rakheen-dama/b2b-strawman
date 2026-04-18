package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerticalModuleRegistryTest {

  private VerticalModuleRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new VerticalModuleRegistry();
  }

  @Test
  void getAllModules_returnsTenModulesWithCorrectIds() {
    var modules = registry.getAllModules();

    assertThat(modules).hasSize(10);
    assertThat(modules)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
            "regulatory_deadlines",
            "lssa_tariff",
            "resource_planning",
            "bulk_billing",
            "automation_builder",
            "disbursements",
            "matter_closure");
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

  @Test
  void getHorizontalModules_returnsThreeHorizontalModules() {
    var horizontal = registry.getHorizontalModules();

    assertThat(horizontal).hasSize(3);
    assertThat(horizontal)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder("resource_planning", "bulk_billing", "automation_builder");
    assertThat(horizontal)
        .allSatisfy(m -> assertThat(m.category()).isEqualTo(ModuleCategory.HORIZONTAL));
  }

  @Test
  void getModule_resourcePlanningIsHorizontalWithCorrectNavItems() {
    var module = registry.getModule("resource_planning");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Resource Planning");
    assertThat(module.get().category()).isEqualTo(ModuleCategory.HORIZONTAL);
    assertThat(module.get().defaultEnabledFor()).isEmpty();
    assertThat(module.get().navItems()).hasSize(2);
    assertThat(module.get().navItems().get(0).path()).isEqualTo("/resources");
    assertThat(module.get().navItems().get(0).label()).isEqualTo("Resources");
    assertThat(module.get().navItems().get(0).zone()).isEqualTo("work");
    assertThat(module.get().navItems().get(1).path()).isEqualTo("/resources/utilization");
    assertThat(module.get().navItems().get(1).label()).isEqualTo("Utilization");
    assertThat(module.get().navItems().get(1).zone()).isEqualTo("work");
  }

  @Test
  void getModule_bulkBillingIsHorizontalWithCorrectNavItem() {
    var module = registry.getModule("bulk_billing");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Bulk Billing Runs");
    assertThat(module.get().category()).isEqualTo(ModuleCategory.HORIZONTAL);
    assertThat(module.get().defaultEnabledFor()).isEmpty();
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/invoices/billing-runs");
    assertThat(module.get().navItems().getFirst().label()).isEqualTo("Billing Runs");
    assertThat(module.get().navItems().getFirst().zone()).isEqualTo("finance");
  }

  @Test
  void getModule_automationBuilderIsHorizontalWithCorrectNavItem() {
    var module = registry.getModule("automation_builder");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Automation Rule Builder");
    assertThat(module.get().category()).isEqualTo(ModuleCategory.HORIZONTAL);
    assertThat(module.get().defaultEnabledFor()).isEmpty();
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/settings/automations");
    assertThat(module.get().navItems().getFirst().label()).isEqualTo("Automations");
    assertThat(module.get().navItems().getFirst().zone()).isEqualTo("work");
  }

  @Test
  void getModule_allExistingModulesAreVertical() {
    var verticalIds =
        List.of(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
            "regulatory_deadlines",
            "lssa_tariff",
            "disbursements",
            "matter_closure");
    for (String id : verticalIds) {
      var module = registry.getModule(id);
      assertThat(module).as("module %s should be present", id).isPresent();
      assertThat(module.get().category())
          .as("module %s should be VERTICAL", id)
          .isEqualTo(ModuleCategory.VERTICAL);
    }
  }

  @Test
  void getModulesByCategory_returnsSevenVerticalModules() {
    var vertical = registry.getModulesByCategory(ModuleCategory.VERTICAL);

    assertThat(vertical).hasSize(7);
    assertThat(vertical)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
            "regulatory_deadlines",
            "lssa_tariff",
            "disbursements",
            "matter_closure");
  }

  @Test
  void getModule_disbursementsIsActiveWithCorrectNavItems() {
    var module = registry.getModule("disbursements");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Disbursements");
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().category()).isEqualTo(ModuleCategory.VERTICAL);
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/legal/disbursements");
    assertThat(module.get().navItems().getFirst().label()).isEqualTo("Disbursements");
    assertThat(module.get().navItems().getFirst().zone()).isEqualTo("legal");
  }
}
