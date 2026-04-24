package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the two reconciliation behaviours exercised by {@link
 * VerticalProfileReconciliationSeeder}:
 *
 * <ul>
 *   <li>GAP-L-27 — legal-za tenants end up with {@code tax_label='VAT'} on org_settings and a
 *       renamed default tax_rate {@code 'VAT — Standard'}, not the bare {@code 'Standard'} inserted
 *       by V43.
 *   <li>GAP-L-44 — enabled_modules on the tenant row contain every module declared in the
 *       vertical-profile JSON (idempotent — re-runs do not duplicate).
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerticalProfileReconciliationSeederTest {

  private static final String ORG_ID = "org_profile_reconcile_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private VerticalProfileReconciliationSeeder reconciliationSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Profile Reconcile Test Org", "legal-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void provisioning_appliesLegalZaTaxDefaultsAndModules() {
    // Provisioning already invokes the seeder — inspect the resulting tenant state.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();

                  // GAP-L-27: tax_label set to 'VAT' from the profile's taxDefaults.
                  assertThat(settings.getTaxLabel()).isEqualTo("VAT");

                  // GAP-L-44: enabled_modules superset includes every legal-za profile module.
                  assertThat(settings.getEnabledModules())
                      .contains(
                          "court_calendar",
                          "conflict_check",
                          "lssa_tariff",
                          "trust_accounting",
                          "disbursements",
                          "matter_closure",
                          "deadlines",
                          "information_requests");

                  // GAP-L-27: legacy 'Standard' rate renamed to 'VAT — Standard', still default,
                  // still 15.00%.
                  var defaultRate = taxRateRepository.findByIsDefaultTrue().orElseThrow();
                  assertThat(defaultRate.getName()).isEqualTo("VAT — Standard");
                  assertThat(defaultRate.getRate()).isEqualByComparingTo(new BigDecimal("15.00"));
                  assertThat(defaultRate.isDefault()).isTrue();
                }));
  }

  @Test
  void reconciliation_isIdempotent() {
    // Capture counts + names before re-running.
    Object[] before = snapshot();

    reconciliationSeeder.reconcile(tenantSchema, ORG_ID);
    reconciliationSeeder.reconcile(tenantSchema, ORG_ID);

    Object[] after = snapshot();

    assertThat(after[0]).isEqualTo(before[0]); // modules size
    assertThat(after[1]).isEqualTo(before[1]); // default tax_rate name
    assertThat(after[2]).isEqualTo(before[2]); // tax_label
    assertThat(after[3]).isEqualTo(before[3]); // tax_rates table row count
  }

  @Test
  void reconciliation_preservesOwnerAddedHorizontalModules() {
    // Simulate an owner who already added a horizontal module the profile does not declare. The
    // reconciliation must union the profile modules in without dropping the custom one.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  var modules = new java.util.ArrayList<>(settings.getEnabledModules());
                  if (!modules.contains("accounting")) {
                    modules.add("accounting");
                  }
                  settings.setEnabledModules(modules);
                  orgSettingsRepository.save(settings);
                }));

    reconciliationSeeder.reconcile(tenantSchema, ORG_ID);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getEnabledModules()).contains("accounting");
                  assertThat(settings.getEnabledModules()).contains("trust_accounting");
                }));
  }

  private Object[] snapshot() {
    Object[] out = new Object[4];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  out[0] = settings.getEnabledModules().size();
                  out[2] = settings.getTaxLabel();
                  out[1] =
                      taxRateRepository.findByIsDefaultTrue().map(TaxRate::getName).orElse(null);
                  out[3] = taxRateRepository.count();
                }));
    return out;
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, java.util.UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
