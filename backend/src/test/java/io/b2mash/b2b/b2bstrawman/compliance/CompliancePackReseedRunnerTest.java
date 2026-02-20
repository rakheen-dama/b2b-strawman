package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class CompliancePackReseedRunnerTest {

  private static final String ORG_ID = "org_reseed_runner_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CompliancePackReseedRunner reseedRunner;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Reseed Runner Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void reseedRunnerIsIdempotent() {
    // The tenant was already seeded during provisioning.
    // Running the reseed runner again should not duplicate anything.
    reseedRunner.run(null);

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
  void reseedRunnerPreservesCompliancePackStatus() {
    reseedRunner.run(null);

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

  private void runInTenant(String schema, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
