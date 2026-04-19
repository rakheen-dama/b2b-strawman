package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
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
class LegalPackSeederIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_lps_legal";
  private static final String ACCOUNTING_ORG_ID = "org_lps_accounting";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private String accountingSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Law Firm", "legal-za");
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ACCOUNTING_ORG_ID, "Accounting Firm", "accounting-za");
    accountingSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();
  }

  @Test
  void legalTenantGetsLegalFieldPacks() {
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var legalCustomerFields =
                      customerFields.stream()
                          .filter(f -> "legal-za-customer".equals(f.getPackId()))
                          .toList();
                  // Epic 462: legal-za-customer slimmed after promoting registration_number,
                  // client_type, physical_address to structural columns. Remaining JSONB fields:
                  // id_passport_number, postal_address, preferred_correspondence, referred_by.
                  assertThat(legalCustomerFields)
                      .extracting(f -> f.getSlug())
                      .containsExactlyInAnyOrder(
                          "id_passport_number",
                          "postal_address",
                          "preferred_correspondence",
                          "referred_by");

                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var legalProjectFields =
                      projectFields.stream()
                          .filter(f -> "legal-za-project".equals(f.getPackId()))
                          .toList();
                  // Epic 462: legal-za-project slimmed after promoting matter_type to a structural
                  // column. Remaining JSONB fields:
                  assertThat(legalProjectFields)
                      .extracting(f -> f.getSlug())
                      .containsExactlyInAnyOrder(
                          "case_number",
                          "court_name",
                          "opposing_party",
                          "opposing_attorney",
                          "advocate_name",
                          "date_of_instruction",
                          "estimated_value");
                }));
  }

  @Test
  void legalTenantGetsLegalTemplates() {
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var legalTemplates =
                      templates.stream().filter(t -> "legal-za".equals(t.getPackId())).toList();
                  // Phase 67, Epic 489B added matter-closure-letter (11th template).
                  // Phase 67, Epic 491A added statement-of-account (12th template).
                  // Phase 67, Epic 492B added 4 conveyancing templates (13th-16th):
                  // offer-to-purchase, deed-of-transfer, power-of-attorney-to-pass-transfer,
                  // bond-cancellation-instruction.
                  assertThat(legalTemplates).hasSize(16);
                  assertThat(legalTemplates)
                      .extracting(t -> t.getSlug())
                      .contains(
                          "matter-closure-letter",
                          "statement-of-account",
                          "offer-to-purchase",
                          "deed-of-transfer",
                          "power-of-attorney-to-pass-transfer",
                          "bond-cancellation-instruction");
                }));
  }

  @Test
  void legalTenantGetsLegalComplianceChecklist() {
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // GAP-S4-02: the legal pack is now split by customerType. Verify both the
                  // INDIVIDUAL and TRUST variants are seeded for legal-za tenants.
                  var individual =
                      checklistTemplateRepository.findBySlug(
                          "legal-za-individual-client-onboarding");
                  assertThat(individual).isPresent();

                  var trust =
                      checklistTemplateRepository.findBySlug("legal-za-trust-client-onboarding");
                  assertThat(trust).isPresent();

                  // GAP-S4-02 regression guard: ensure the TRUST pack actually loaded all
                  // 12 FICA checklist items and that the item-key dependencies resolved to
                  // real item IDs during seeding (second pass of CompliancePackSeeder).
                  var trustTemplate = trust.orElseThrow();
                  assertThat(trustTemplate.getCustomerType()).isEqualTo("TRUST");

                  var trustItems =
                      checklistTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          trustTemplate.getId());
                  assertThat(trustItems).hasSize(12);

                  // Locate the dependency-anchor items by name (slugs generated from names).
                  var proofOfTrustBanking =
                      trustItems.stream()
                          .filter(i -> "Proof of Trust Banking".equals(i.getName()))
                          .findFirst()
                          .orElseThrow();
                  var trustee1Id =
                      trustItems.stream()
                          .filter(i -> "Trustee 1 ID".equals(i.getName()))
                          .findFirst()
                          .orElseThrow();

                  // FICA Risk Assessment depends on proof-of-trust-banking.
                  var riskAssessment =
                      trustItems.stream()
                          .filter(i -> "FICA Risk Assessment".equals(i.getName()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(riskAssessment.getDependsOnItemId())
                      .isEqualTo(proofOfTrustBanking.getId());

                  // Sanctions Screening depends on trustee-1-id.
                  var sanctions =
                      trustItems.stream()
                          .filter(i -> "Sanctions Screening".equals(i.getName()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(sanctions.getDependsOnItemId()).isEqualTo(trustee1Id.getId());

                  // And the INDIVIDUAL variant should not bleed into the TRUST count.
                  ChecklistTemplate individualTpl = individual.orElseThrow();
                  assertThat(individualTpl.getCustomerType()).isEqualTo("INDIVIDUAL");
                }));
  }

  @Test
  void accountingTenantDoesNotGetLegalFieldPacks() {
    runInTenant(
        accountingSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var legalFields =
                      customerFields.stream()
                          .filter(f -> "legal-za-customer".equals(f.getPackId()))
                          .toList();
                  assertThat(legalFields).isEmpty();

                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var legalProjectFields =
                      projectFields.stream()
                          .filter(f -> "legal-za-project".equals(f.getPackId()))
                          .toList();
                  assertThat(legalProjectFields).isEmpty();
                }));
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
