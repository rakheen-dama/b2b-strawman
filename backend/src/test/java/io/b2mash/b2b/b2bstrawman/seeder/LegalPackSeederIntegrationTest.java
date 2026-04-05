package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
                  assertThat(legalCustomerFields).hasSize(7);

                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var legalProjectFields =
                      projectFields.stream()
                          .filter(f -> "legal-za-project".equals(f.getPackId()))
                          .toList();
                  assertThat(legalProjectFields).hasSize(8);
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
                  assertThat(legalTemplates).hasSize(10);
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
                  var template =
                      checklistTemplateRepository.findBySlug("legal-za-client-onboarding");
                  assertThat(template).isPresent();
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
