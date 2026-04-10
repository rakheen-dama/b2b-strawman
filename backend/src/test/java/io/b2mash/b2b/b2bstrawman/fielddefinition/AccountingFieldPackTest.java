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
  void accountingTenantGets8CustomerFieldsFromAccountingPack() {
    // Post-Epic-462: 8 promoted fields removed (acct_company_registration_number, vat_number,
    // acct_entity_type, financial_year_end, primary_contact_*, registered_address). Remaining:
    // trading_as, sars_tax_reference, sars_efiling_profile, industry_sic_code, postal_address,
    // fica_verified, fica_verification_date, referred_by.
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

                  assertThat(accountingFields).hasSize(8);

                  var slugs = accountingFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs)
                      .containsExactlyInAnyOrder(
                          "trading_as",
                          "sars_tax_reference",
                          "sars_efiling_profile",
                          "industry_sic_code",
                          "postal_address",
                          "fica_verified",
                          "fica_verification_date",
                          "referred_by");
                  // Promoted slugs are gone from the pack.
                  assertThat(slugs)
                      .doesNotContain(
                          "acct_company_registration_number",
                          "vat_number",
                          "acct_entity_type",
                          "financial_year_end",
                          "primary_contact_name",
                          "primary_contact_email",
                          "primary_contact_phone",
                          "registered_address");
                }));
  }

  @Test
  void accountingTenantGets4ProjectFieldsFromAccountingPack() {
    // Post-Epic-462: engagement_type removed (promoted to Project.workType).
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

                  assertThat(accountingFields).hasSize(4);

                  var slugs = accountingFields.stream().map(FieldDefinition::getSlug).toList();
                  assertThat(slugs)
                      .containsExactlyInAnyOrder(
                          "tax_year",
                          "sars_submission_deadline",
                          "assigned_reviewer",
                          "complexity");
                  assertThat(slugs).doesNotContain("engagement_type");
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
