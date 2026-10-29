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
  void getAllModules_returnsEightModulesWithCorrectIds() {
    var modules = registry.getAllModules();

    assertThat(modules).hasSize(8);
    assertThat(modules)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
            "lssa_tariff",
            "bulk_billing",
            "information_requests",
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
  void getModule_courtCalendarIsActiveCourtDatesWithNoNavItems() {
    // 628D (N-7, R-2): the id stays — it gates the Diary court rows, the matter Court dates tab,
    // the reminder job and the prescription tracker — but the dead /court-calendar page link is
    // gone
    // and the display name follows the product wording.
    var module = registry.getModule("court_calendar");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Court dates");
    assertThat(module.get().status()).isEqualTo("active");
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).isEmpty();
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
  void getHorizontalModules_returnsTwoHorizontalModules() {
    var horizontal = registry.getHorizontalModules();

    assertThat(horizontal).hasSize(2);
    assertThat(horizontal)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder("bulk_billing", "information_requests");
    assertThat(horizontal)
        .allSatisfy(m -> assertThat(m.category()).isEqualTo(ModuleCategory.HORIZONTAL));
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
  void getModule_informationRequestsIsHorizontalEnabledForLegalZa() {
    var module = registry.getModule("information_requests");

    assertThat(module).isPresent();
    assertThat(module.get().name()).isEqualTo("Information Requests");
    assertThat(module.get().category()).isEqualTo(ModuleCategory.HORIZONTAL);
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
    assertThat(module.get().navItems()).hasSize(1);
    assertThat(module.get().navItems().getFirst().path()).isEqualTo("/information-requests");
    assertThat(module.get().navItems().getFirst().label()).isEqualTo("Information Requests");
    assertThat(module.get().navItems().getFirst().zone()).isEqualTo("clients");
  }

  @Test
  void getModule_removedModulesAreAbsent() {
    // Phase 86 (Epic 604A): the accounting-only regulatory deadlines module and the automation
    // rule builder module were removed from the registry (ADR-332 / ADR-334).
    assertThat(registry.getModule("regulatory_deadlines")).isEmpty();
    assertThat(registry.getModule("automation_builder")).isEmpty();
  }

  @Test
  void getModule_allExistingModulesAreVertical() {
    var verticalIds =
        List.of(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
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
  void getModulesByCategory_returnsSixVerticalModules() {
    var vertical = registry.getModulesByCategory(ModuleCategory.VERTICAL);

    assertThat(vertical).hasSize(6);
    assertThat(vertical)
        .extracting(VerticalModuleRegistry.ModuleDefinition::id)
        .containsExactlyInAnyOrder(
            "trust_accounting",
            "court_calendar",
            "conflict_check",
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
