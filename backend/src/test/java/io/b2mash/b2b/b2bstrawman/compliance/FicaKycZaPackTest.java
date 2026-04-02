package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class FicaKycZaPackTest {

  private static final String ACCOUNTING_ORG_ID = "org_fica_accounting";
  private static final String GENERIC_ORG_ID = "org_fica_generic";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private ChecklistTemplateItemRepository checklistTemplateItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String accountingSchema;
  private String genericSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ACCOUNTING_ORG_ID, "FICA Accounting Test Org", "accounting-za");
    accountingSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();

    provisioningService.provisionTenant(GENERIC_ORG_ID, "FICA Generic Test Org", null);
    genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void accountingTenantGetsFicaKycChecklistWith11Items() {
    runInTenant(
        accountingSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      checklistTemplateRepository
                          .findBySlug("fica-kyc-za-accounting")
                          .orElseThrow(
                              () ->
                                  new AssertionError(
                                      "FICA KYC checklist template not found for accounting tenant"));
                  var items =
                      checklistTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());

                  // 9 original items + 2 trust-specific items (Letters of Authority, Trust Deed)
                  assertThat(items).hasSize(11);
                  assertThat(template.getName()).isEqualTo("FICA KYC — SA Accounting");
                }));
  }

  @Test
  void nonAccountingTenantDoesNotGetFicaKycChecklist() {
    runInTenant(
        genericSchema,
        GENERIC_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template = checklistTemplateRepository.findBySlug("fica-kyc-za-accounting");

                  assertThat(template).isEmpty();
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
