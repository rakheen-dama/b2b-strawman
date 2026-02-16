package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompliancePackSeederTest {

  private static final String ORG_ID = "org_cps_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private RetentionPolicyRepository retentionPolicyRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CompliancePackSeeder compliancePackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "CPS Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void findsThreePacksOnClasspath() {
    List<CompliancePackDefinition> packs = compliancePackSeeder.loadPacks();
    assertThat(packs).hasSize(3);

    List<String> packIds = packs.stream().map(CompliancePackDefinition::packId).toList();
    assertThat(packIds)
        .containsExactlyInAnyOrder("generic-onboarding", "sa-fica-individual", "sa-fica-company");
  }

  @Test
  void createsChecklistTemplatesFromPacks() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = checklistTemplateRepository.findAll();
                  List<String> packIds =
                      templates.stream()
                          .filter(t -> t.getPackId() != null)
                          .filter(
                              t ->
                                  t.getPackId().equals("generic-onboarding")
                                      || t.getPackId().equals("sa-fica-individual")
                                      || t.getPackId().equals("sa-fica-company"))
                          .map(ChecklistTemplate::getPackId)
                          .toList();
                  assertThat(packIds)
                      .containsExactlyInAnyOrder(
                          "generic-onboarding", "sa-fica-individual", "sa-fica-company");
                }));
  }

  @Test
  void genericOnboardingIsActiveByDefault() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      checklistTemplateRepository.findBySlug("generic-onboarding").orElseThrow();
                  assertThat(template.isActive()).isTrue();
                  assertThat(template.isAutoInstantiate()).isTrue();
                  assertThat(template.getCustomerType()).isEqualTo("ANY");
                  assertThat(template.getSource()).isEqualTo("PLATFORM");

                  var items =
                      checklistTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());
                  assertThat(items).hasSize(4);
                }));
  }

  @Test
  void saFicaPacksAreInactiveAutoInstantiateByDefault() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var individual =
                      checklistTemplateRepository.findBySlug("sa-fica-individual").orElseThrow();
                  assertThat(individual.isActive()).isTrue();
                  assertThat(individual.isAutoInstantiate()).isFalse();

                  var company =
                      checklistTemplateRepository.findBySlug("sa-fica-company").orElseThrow();
                  assertThat(company.isActive()).isTrue();
                  assertThat(company.isAutoInstantiate()).isFalse();
                }));
  }

  @Test
  void reSeededPacksAreIdempotent() {
    // Call seeder again — should be idempotent
    compliancePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);

    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = checklistTemplateRepository.findAll();
                  long complianceTemplateCount =
                      templates.stream()
                          .filter(t -> t.getPackId() != null)
                          .filter(
                              t ->
                                  t.getPackId().equals("generic-onboarding")
                                      || t.getPackId().equals("sa-fica-individual")
                                      || t.getPackId().equals("sa-fica-company"))
                          .count();
                  assertThat(complianceTemplateCount).isEqualTo(3);

                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  assertThat(settings.getCompliancePackStatus()).hasSize(3);
                }));
  }

  @Test
  void saFicaIndividualFieldDefinitionsCreated() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saIdNumber =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "sa_id_number");
                  assertThat(saIdNumber).isPresent();

                  var passportNumber =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "passport_number");
                  assertThat(passportNumber).isPresent();

                  var riskRating =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "risk_rating");
                  assertThat(riskRating).isPresent();
                  assertThat(riskRating.get().getOptions()).isNotNull();
                  assertThat(riskRating.get().getOptions()).hasSize(3);
                }));
  }

  @Test
  void saFicaCompanyFieldDefinitionsCreated() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var regNumber =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "company_registration_number");
                  assertThat(regNumber).isPresent();

                  var entityType =
                      fieldDefinitionRepository.findByEntityTypeAndSlug(
                          EntityType.CUSTOMER, "entity_type");
                  assertThat(entityType).isPresent();
                  assertThat(entityType.get().getOptions()).isNotNull();
                  assertThat(entityType.get().getOptions()).hasSize(4);
                }));
  }

  @Test
  void packsWithoutRetentionOverridesCreateNoExtraPolicies() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Only the 2 default retention policies should exist (seeded by provisioning)
                  var policies = retentionPolicyRepository.findAll();
                  assertThat(policies).hasSize(2);
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
