package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustAccountingModuleRegistrationTest {

  private VerticalModuleRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new VerticalModuleRegistry();
  }

  @Test
  void trustAccountingModule_isActiveWithSevenNavItems() {
    var module = registry.getModule("trust_accounting");

    assertThat(module).isPresent();

    var m = module.get();
    assertThat(m.status()).isEqualTo("active");
    assertThat(m.navItems()).hasSize(7);
    assertThat(m.navItems().get(0).path()).isEqualTo("/trust-accounting");
    assertThat(m.navItems().get(0).label()).isEqualTo("Trust Accounting");
    assertThat(m.navItems().get(0).zone()).isEqualTo("legal");
    assertThat(m.navItems().get(1).path()).isEqualTo("/trust-accounting/transactions");
    assertThat(m.navItems().get(1).label()).isEqualTo("Transactions");
    assertThat(m.navItems().get(2).path()).isEqualTo("/trust-accounting/client-ledgers");
    assertThat(m.navItems().get(2).label()).isEqualTo("Client Ledgers");
    assertThat(m.navItems().get(3).path()).isEqualTo("/trust-accounting/reconciliation");
    assertThat(m.navItems().get(3).label()).isEqualTo("Reconciliation");
    assertThat(m.navItems().get(4).path()).isEqualTo("/trust-accounting/interest");
    assertThat(m.navItems().get(4).label()).isEqualTo("Interest");
    assertThat(m.navItems().get(5).path()).isEqualTo("/trust-accounting/investments");
    assertThat(m.navItems().get(5).label()).isEqualTo("Investments");
    assertThat(m.navItems().get(6).path()).isEqualTo("/trust-accounting/reports");
    assertThat(m.navItems().get(6).label()).isEqualTo("Trust Reports");
  }

  @Test
  void trustAccountingModule_defaultEnabledForLegalZa() {
    var module = registry.getModule("trust_accounting");

    assertThat(module).isPresent();
    assertThat(module.get().defaultEnabledFor()).containsExactly("legal-za");
  }
}
