package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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
class AccountingFieldPackTest {

  private static final String ORG_ID = "org_afp_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Accounting Field Pack Test Org", "accounting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void accountingTenantGets16CustomerFieldsFromAccountingPack() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);
                  var accountingFields =
                      customerFields.stream()
                          .filter(f -> "accounting-za-customer".equals(f.getPackId()))
                          .toList();

                  assertThat(accountingFields).hasSize(16);

                  // Verify some key fields are present
                  var slugs = accountingFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs)
                      .contains(
                          "acct_company_registration_number",
                          "vat_number",
                          "sars_tax_reference",
                          "financial_year_end",
                          "acct_entity_type",
                          "fica_verified");
                }));
  }

  @Test
  void accountingTenantGets5ProjectFieldsFromAccountingPack() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var projectFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.PROJECT);
                  var accountingFields =
                      projectFields.stream()
                          .filter(f -> "accounting-za-project".equals(f.getPackId()))
                          .toList();

                  assertThat(accountingFields).hasSize(5);

                  var slugs = accountingFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs)
                      .contains(
                          "engagement_type",
                          "tax_year",
                          "sars_submission_deadline",
                          "assigned_reviewer",
                          "complexity");
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
