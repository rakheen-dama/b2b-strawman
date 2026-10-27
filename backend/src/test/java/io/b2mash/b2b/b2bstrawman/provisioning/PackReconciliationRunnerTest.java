package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.packs.PackInstallRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.Map;
import java.util.TreeMap;
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
  private static final String GATE_ORG_ID = "org_pack_reconcile_gate_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PackReconciliationRunner reconciliationRunner;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private PackInstallRepository packInstallRepository;

  private String tenantSchema;
  private String gateSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Pack Reconciliation Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    provisioningService.provisionTenant(GATE_ORG_ID, "Pack Reconcile Gate Test Org", "legal-za");
    gateSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GATE_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void reconciliationRunnerIsIdempotentAndPreservesSeededState() {
    // Tenant was seeded during provisioning. Reconcile just THIS tenant (not every schema in the
    // shared DB) to verify completion, state preservation, and no-throw in a single pass — the
    // startup runner already exercises the all-tenant loop at every context boot.
    assertThatCode(() -> reconciliationRunner.reconcilePacksForTenant(tenantSchema, ORG_ID))
        .doesNotThrowAnyException();

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var complianceTemplates =
                      templateRepository.findAll().stream()
                          .filter(t -> "PLATFORM".equals(t.getSource()) && t.getPackId() != null)
                          .filter(
                              t ->
                                  t.getPackId().equals("generic-onboarding")
                                      || t.getPackId().equals("sa-fica-individual")
                                      || t.getPackId().equals("sa-fica-company"))
                          .toList();
                  assertThat(complianceTemplates).hasSize(3);

                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getPackStatus().getCompliancePackStatus()).isNotNull();
                  assertThat(settings.getPackStatus().getCompliancePackStatus()).hasSize(3);
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

    // Run reconciliation twice (scoped to this test's tenant — see reconcilePacksForTenant)
    reconciliationRunner.reconcilePacksForTenant(profileSchema, profileOrgId);
    reconciliationRunner.reconcilePacksForTenant(profileSchema, profileOrgId);

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

  // --- Epic 625E (ADR-356): the reconcile version gate through the real runner ---

  @Test
  void gateSkipsAtEqualVersion() {
    Map<String, String> before = gateVersions();

    reconciliationRunner.reconcilePacksForTenant(gateSchema, GATE_ORG_ID);

    assertThat(gateVersions()).isEqualTo(before);
  }

  @Test
  void gateRunsAtLowerVersionAndRecordsClasspathVersion() {
    rewind("common-project", "2", "1"); // FIELD
    rewind("standard-reports", "1", "0"); // REPORT
    rewind("fica-onboarding-pack", "1", "0"); // REQUEST
    rewind("automation-common", "2", "1"); // AUTOMATION_TEMPLATE (installer-managed)

    reconciliationRunner.reconcilePacksForTenant(gateSchema, GATE_ORG_ID);

    Map<String, String> after = gateVersions();
    assertThat(after)
        .containsEntry("common-project", "2")
        .containsEntry("standard-reports", "1")
        .containsEntry("fica-onboarding-pack", "1")
        .containsEntry("automation-common", "2");

    // AutomationPackInstaller.reconcile returned true → PackInstallService re-recorded the pack
    // in the OrgSettings automation list at the classpath version.
    runInTenant(
        gateSchema,
        GATE_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var entries =
                      orgSettingsRepository
                          .findForCurrentTenant()
                          .orElseThrow()
                          .getPackStatus()
                          .getAutomationPackStatus();
                  assertThat(entries)
                      .filteredOn(e -> "automation-common".equals(e.get("packId")))
                      .singleElement()
                      .satisfies(
                          e -> assertThat(((Number) e.get("version")).intValue()).isEqualTo(2));
                }));
  }

  @Test
  void gateClosedOnNonNumericVersion() {
    rewind("legal-za-clauses", "1", "v1-legacy");
    try {
      assertThatCode(() -> reconciliationRunner.reconcilePacksForTenant(gateSchema, GATE_ORG_ID))
          .doesNotThrowAnyException();
      assertThat(gateVersions()).containsEntry("legal-za-clauses", "v1-legacy");
    } finally {
      rewind("legal-za-clauses", "v1-legacy", "1");
    }
  }

  /** Rewinds (or restores) a pack_install version with the gate's compare-and-set query. */
  private void rewind(String packId, String from, String to) {
    runInTenant(
        gateSchema,
        GATE_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    assertThat(packInstallRepository.compareAndSetVersion(packId, from, to))
                        .as("rewind %s %s -> %s", packId, from, to)
                        .isEqualTo(1)));
  }

  private Map<String, String> gateVersions() {
    Map<String, String> versions = new TreeMap<>();
    runInTenant(
        gateSchema,
        GATE_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    packInstallRepository
                        .findAll()
                        .forEach(r -> versions.put(r.getPackId(), r.getPackVersion()))));
    return versions;
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
