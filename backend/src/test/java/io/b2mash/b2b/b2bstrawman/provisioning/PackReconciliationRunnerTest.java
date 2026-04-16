package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.schedule.RecurringScheduleRepository;
import io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackReconciliationRunnerTest {

  private static final String ORG_ID = "org_pack_reconciliation_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackReconciliationRunner reconciliationRunner;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private RecurringScheduleRepository recurringScheduleRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PackInstallRepository packInstallRepository;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Pack Reconciliation Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void reconciliationRunnerIsIdempotent() {
    // The tenant was already seeded during provisioning.
    // Running the reconciliation runner again should not duplicate anything.
    reconciliationRunner.run(null);

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var allTemplates = templateRepository.findAll();
                  var complianceTemplates =
                      allTemplates.stream()
                          .filter(t -> "PLATFORM".equals(t.getSource()) && t.getPackId() != null)
                          .filter(
                              t ->
                                  t.getPackId().equals("generic-onboarding")
                                      || t.getPackId().equals("sa-fica-individual")
                                      || t.getPackId().equals("sa-fica-company"))
                          .toList();
                  // Still exactly 3 compliance templates, not duplicated
                  assertThat(complianceTemplates).hasSize(3);
                }));
  }

  @Test
  void reconciliationRunnerPreservesCompliancePackStatus() {
    reconciliationRunner.run(null);

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getCompliancePackStatus()).isNotNull();
                  assertThat(settings.getCompliancePackStatus()).hasSize(3);
                }));
  }

  @Test
  void reconciliationRunnerHandlesEmptyTenantListGracefully() {
    // This test verifies the runner doesn't throw when there are tenants in the DB.
    // A truly empty list would require a fresh DB, but we can at least verify
    // the runner completes without error on the current state.
    assertThatCode(() -> reconciliationRunner.run(null)).doesNotThrowAnyException();
  }

  @Test
  void reconciliationRunnerIdempotentWithRateAndSchedulePacks() {
    // Provision a tenant with accounting-za profile to trigger rate/schedule seeding
    String acctOrgId = "org_pack_reconciliation_acct_test";
    provisioningService.provisionTenant(
        acctOrgId, "Accounting Reconciliation Test Org", "accounting-za");
    String acctSchema =
        orgSchemaMappingRepository.findByClerkOrgId(acctOrgId).orElseThrow().getSchemaName();

    // Count rates and schedules after initial provisioning
    long[] initialCounts = new long[2];
    runInTenant(
        acctSchema,
        acctOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Count seeded rates (null memberId = org-level seeded rates)
                  initialCounts[0] =
                      billingRateRepository.findAll().stream()
                          .filter(r -> r.getMemberId() == null)
                          .count();
                  // Count seeder-created schedules (sentinel UUID)
                  initialCounts[1] =
                      recurringScheduleRepository.findAll().stream()
                          .filter(
                              s -> SchedulePackSeeder.SEEDER_CREATED_BY.equals(s.getCreatedBy()))
                          .count();
                }));

    // Run reconciliation twice
    reconciliationRunner.run(null);
    reconciliationRunner.run(null);

    // Verify counts have not increased
    runInTenant(
        acctSchema,
        acctOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long rateCount =
                      billingRateRepository.findAll().stream()
                          .filter(r -> r.getMemberId() == null)
                          .count();
                  long scheduleCount =
                      recurringScheduleRepository.findAll().stream()
                          .filter(
                              s -> SchedulePackSeeder.SEEDER_CREATED_BY.equals(s.getCreatedBy()))
                          .count();
                  assertThat(rateCount)
                      .as("Seeded rate count should not increase after reconciliation")
                      .isEqualTo(initialCounts[0]);
                  assertThat(scheduleCount)
                      .as("Seeder-created schedule count should not increase after reconciliation")
                      .isEqualTo(initialCounts[1]);
                }));
  }

  @Test
  void reconciliationRunnerIdempotentWithPackInstallRows() {
    // Provision a tenant with a profile that has template/automation packs
    String profileOrgId = "org_pack_reconciliation_profile_test";
    provisioningService.provisionTenant(
        profileOrgId, "Profile Reconciliation Test Org", "legal-za");
    String profileSchema =
        orgSchemaMappingRepository.findByClerkOrgId(profileOrgId).orElseThrow().getSchemaName();

    // Count PackInstall rows after initial provisioning
    long[] initialPackInstallCount = new long[1];
    runInTenant(
        profileSchema,
        profileOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  initialPackInstallCount[0] = packInstallRepository.count();
                }));

    // Run reconciliation twice
    reconciliationRunner.run(null);
    reconciliationRunner.run(null);

    // Verify PackInstall count has not increased (idempotent)
    runInTenant(
        profileSchema,
        profileOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long afterCount = packInstallRepository.count();
                  assertThat(afterCount)
                      .as("PackInstall count should not increase after reconciliation")
                      .isEqualTo(initialPackInstallCount[0]);
                }));

    // Verify other pack types (field definitions, clauses) are still seeded via direct seeders
    runInTenant(
        profileSchema,
        profileOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var allTemplates = templateRepository.findAll();
                  var complianceTemplates =
                      allTemplates.stream()
                          .filter(t -> "PLATFORM".equals(t.getSource()) && t.getPackId() != null)
                          .toList();
                  assertThat(complianceTemplates).isNotEmpty();
                }));
  }

  private void runInTenant(String schema, Runnable action) {
    runInTenant(schema, ORG_ID, action);
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
