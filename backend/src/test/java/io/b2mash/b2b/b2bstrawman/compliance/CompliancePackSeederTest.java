package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
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
class CompliancePackSeederTest {

  private static final String ORG_ID = "org_compliance_seeder_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private RetentionPolicyRepository retentionPolicyRepository;
  @Autowired private CompliancePackSeeder compliancePackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Compliance Seeder Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedCreatesTemplatesFromAllThreePacks() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Template pack seeder also creates templates, so filter by source=PLATFORM
                  // and packId starting with compliance pack IDs
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
                  assertThat(complianceTemplates).hasSize(3);
                }));
  }

  @Test
  void genericPackHasFourItems() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("generic-client-onboarding").orElseThrow();
                  var items =
                      templateItemRepository.findByTemplateIdOrderBySortOrder(template.getId());
                  assertThat(items).hasSize(4);
                }));
  }

  @Test
  void ficaIndividualPackHasFiveItems() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("sa-fica-individual-onboarding").orElseThrow();
                  var items =
                      templateItemRepository.findByTemplateIdOrderBySortOrder(template.getId());
                  assertThat(items).hasSize(5);
                }));
  }

  @Test
  void ficaCompanyPackHasSixItems() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("sa-fica-company-onboarding").orElseThrow();
                  var items =
                      templateItemRepository.findByTemplateIdOrderBySortOrder(template.getId());
                  assertThat(items).hasSize(6);
                }));
  }

  @Test
  void ficaIndividualFieldsCrossSeeded() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saId =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "sa_id_number");
                  assertThat(saId).isPresent();

                  var passport =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "passport_number");
                  assertThat(passport).isPresent();

                  var riskRating =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "risk_rating");
                  assertThat(riskRating).isPresent();
                  assertThat(riskRating.get().getOptions()).isNotNull();
                  assertThat(riskRating.get().getOptions()).hasSize(3);
                }));
  }

  @Test
  void idempotencySecondSeedCallCreatesNothing() {
    // Re-seed — should skip all packs already applied
    compliancePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
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
                  assertThat(complianceTemplates).hasSize(3); // still 3, not 6
                }));
  }

  @Test
  void compliancePackStatusHasThreeEntries() {
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
  void genericTemplateSourceIsPlatform() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("generic-client-onboarding").orElseThrow();
                  assertThat(template.getSource()).isEqualTo("PLATFORM");
                  assertThat(template.getPackId()).isEqualTo("generic-onboarding");
                  assertThat(template.isActive()).isTrue();
                }));
  }

  @Test
  void ficaItemsHaveCorrectDependencies() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.findBySlug("sa-fica-individual-onboarding").orElseThrow();
                  var items =
                      templateItemRepository.findByTemplateIdOrderBySortOrder(template.getId());

                  // Item 1 (sort 1): Verify identity document — no dependency
                  assertThat(items.get(0).getDependsOnItemId()).isNull();
                  // Item 2 (sort 2): Verify proof of address — no dependency
                  assertThat(items.get(1).getDependsOnItemId()).isNull();
                  // Item 3 (sort 3): Confirm source of funds — no dependency
                  assertThat(items.get(2).getDependsOnItemId()).isNull();
                  // Item 4 (sort 4): Perform risk assessment — depends on item 1
                  assertThat(items.get(3).getDependsOnItemId()).isEqualTo(items.get(0).getId());
                  // Item 5 (sort 5): FICA sign-off — depends on item 4
                  assertThat(items.get(4).getDependsOnItemId()).isEqualTo(items.get(3).getId());
                }));
  }

  @Test
  void ficaIndividualRetentionOverrideCreated() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var policy =
                      retentionPolicyRepository
                          .findByRecordTypeAndTriggerEvent("CUSTOMER", "CUSTOMER_OFFBOARDED")
                          .orElseThrow(() -> new AssertionError("RetentionPolicy not found"));
                  assertThat(policy.getRetentionDays()).isEqualTo(1825);
                  assertThat(policy.getAction()).isEqualTo("FLAG");
                  assertThat(policy.isActive()).isTrue();
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
