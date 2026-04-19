package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.clause.ClauseSource;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTask;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for Epic 492A — conveyancing pack installation. Verifies that a fresh {@code
 * legal-za} tenant receives the conveyancing field pack (10 project fields), the Property Transfer
 * project template (12 tasks), and the conveyancing clause pack (10 Tiptap clauses) via the
 * standard classpath-scanning seeders, and that vertical-profile gating prevents non-legal tenants
 * from receiving any of this content.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConveyancingPackInstallTest {

  private static final String LEGAL_ORG_ID = "org_cvpk_legal_test";
  private static final String ACCOUNTING_ORG_ID = "org_cvpk_acct_test";
  private static final String CONSULTING_ORG_ID = "org_cvpk_cons_test";

  private static final String FIELD_PACK_ID = "conveyancing-za-project";
  private static final String CLAUSE_PACK_ID = "conveyancing-za-clauses";
  private static final String TEMPLATE_NAME = "Property Transfer (Conveyancing)";

  private static final List<String> EXPECTED_FIELD_SLUGS =
      List.of(
          "conveyancing_type",
          "property_address",
          "erf_number",
          "deeds_office",
          "lodgement_date",
          "registration_date",
          "deed_number",
          "purchase_price",
          "transfer_duty",
          "bond_institution");

  private static final List<String> EXPECTED_CLAUSE_SLUGS =
      List.of(
          "voetstoots",
          "occupation-date",
          "suspensive-bond",
          "transfer-duty-liability",
          "fica-compliance",
          "sectional-title-levies",
          "body-corporate-clearance",
          "rates-clearance",
          "cost-of-cancellation",
          "jurisdiction-za");

  private static final List<String> EXPECTED_TASK_NAMES_IN_ORDER =
      List.of(
          "Receive instruction & conflict check",
          "Draft offer to purchase / review OTP",
          "Obtain FICA documentation from parties",
          "Obtain rates clearance figures",
          "Obtain transfer duty receipt",
          "Draft deed of transfer",
          "Draft power of attorney to pass transfer",
          "Lodge documents at Deeds Office",
          "Respond to Deeds Office notes",
          "Registration & collection of title deed",
          "Finalise financial statement & disburse",
          "Close matter");

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private ProjectTemplateRepository projectTemplateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalTenantSchema;
  private String accountingTenantSchema;
  private String consultingTenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Conveyancing Legal Test Org", "legal-za");
    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(
        ACCOUNTING_ORG_ID, "Conveyancing Accounting Test Org", "accounting-za");
    accountingTenantSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();

    provisioningService.provisionTenant(
        CONSULTING_ORG_ID, "Conveyancing Consulting Test Org", "consulting-za");
    consultingTenantSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(CONSULTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();
  }

  @Test
  void legalTenantGetsTenConveyancingProjectFields() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var conveyancingFields = conveyancingFields();

                  assertThat(conveyancingFields).hasSize(10);

                  var slugs = conveyancingFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs).containsExactlyInAnyOrderElementsOf(EXPECTED_FIELD_SLUGS);

                  var dropdownSlugs =
                      conveyancingFields.stream()
                          .filter(f -> f.getFieldType() == FieldType.DROPDOWN)
                          .map(FieldDefinition::getSlug)
                          .toList();
                  assertThat(dropdownSlugs)
                      .containsExactlyInAnyOrder(
                          "conveyancing_type", "deeds_office", "bond_institution");
                }));
  }

  @Test
  void propertyTransferTemplateHasTwelveTasksInOrder() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = propertyTransferTemplate();
                  var tasks =
                      templateTaskRepository.findByTemplateIdOrderBySortOrder(template.getId());

                  assertThat(tasks).hasSize(12);

                  var names = tasks.stream().map(TemplateTask::getName).toList();
                  assertThat(names).containsExactlyElementsOf(EXPECTED_TASK_NAMES_IN_ORDER);

                  var sortOrders = tasks.stream().map(TemplateTask::getSortOrder).toList();
                  var expectedSortOrders =
                      java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList();
                  assertThat(sortOrders).containsExactlyElementsOf(expectedSortOrders);

                  assertThat(tasks)
                      .allSatisfy(
                          task -> {
                            assertThat(task.isBillable()).isTrue();
                            assertThat(task.getAssigneeRole()).isIn("PROJECT_LEAD", "ANY_MEMBER");
                          });
                }));
  }

  @Test
  void legalTenantGetsTenConveyancingClauses() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          CLAUSE_PACK_ID, ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(10);

                  var slugs = clauses.stream().map(Clause::getSlug).toList();
                  assertThat(slugs).containsExactlyInAnyOrderElementsOf(EXPECTED_CLAUSE_SLUGS);
                }));
  }

  @Test
  void conditionalVisibilityRulesAreSetOnDependentFields() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var registrationDate =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "registration_date")
                          .orElseThrow();
                  assertThat(registrationDate.getVisibilityCondition())
                      .isNotNull()
                      .containsEntry("dependsOnSlug", "lodgement_date")
                      .containsEntry("operator", "isSet");

                  var deedNumber =
                      fieldDefinitionRepository
                          .findByEntityTypeAndSlug(EntityType.PROJECT, "deed_number")
                          .orElseThrow();
                  assertThat(deedNumber.getVisibilityCondition())
                      .isNotNull()
                      .containsEntry("dependsOnSlug", "registration_date")
                      .containsEntry("operator", "isSet");
                }));
  }

  @Test
  void legalTenantProvisioningInstallsAllThreeConveyancingPacksWithoutError() {
    runInTenant(
        legalTenantSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // All three packs landed (field + template + clause).
                  assertThat(conveyancingFields()).hasSize(10);
                  assertThat(propertyTransferTemplate()).isNotNull();
                  assertThat(
                          clauseRepository.findByPackIdAndSourceAndActiveTrue(
                              CLAUSE_PACK_ID, ClauseSource.SYSTEM))
                      .hasSize(10);
                }));
  }

  @Test
  void nonLegalTenantsDoNotReceiveConveyancingContent() {
    // Accounting-za tenant — should not see conveyancing field pack or clause pack.
    runInTenant(
        accountingTenantSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var conveyancing =
                      projectFields.stream()
                          .filter(f -> FIELD_PACK_ID.equals(f.getPackId()))
                          .toList();
                  assertThat(conveyancing).isEmpty();

                  assertThat(
                          clauseRepository.findByPackIdAndSourceAndActiveTrue(
                              CLAUSE_PACK_ID, ClauseSource.SYSTEM))
                      .isEmpty();
                }));

    // Consulting-za tenant — same assertion.
    runInTenant(
        consultingTenantSchema,
        CONSULTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var conveyancing =
                      projectFields.stream()
                          .filter(f -> FIELD_PACK_ID.equals(f.getPackId()))
                          .toList();
                  assertThat(conveyancing).isEmpty();

                  assertThat(
                          clauseRepository.findByPackIdAndSourceAndActiveTrue(
                              CLAUSE_PACK_ID, ClauseSource.SYSTEM))
                      .isEmpty();
                }));
  }

  private List<FieldDefinition> conveyancingFields() {
    return fieldDefinitionRepository
        .findByEntityTypeAndActiveTrueOrderBySortOrder(EntityType.PROJECT)
        .stream()
        .filter(f -> FIELD_PACK_ID.equals(f.getPackId()))
        .toList();
  }

  private ProjectTemplate propertyTransferTemplate() {
    return projectTemplateRepository.findAllByOrderByNameAsc().stream()
        .filter(t -> "SEEDER".equals(t.getSource()))
        .filter(t -> TEMPLATE_NAME.equals(t.getName()))
        .findFirst()
        .orElseThrow();
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
