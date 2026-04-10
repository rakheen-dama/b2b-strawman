package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
class VerticalProfileFilteringTest {

  private static final String ACCOUNTING_ORG_ID = "org_vpf_accounting";
  private static final String GENERIC_ORG_ID = "org_vpf_generic";
  private static final String LAW_ORG_ID = "org_vpf_law";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private FieldPackSeeder fieldPackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String accountingSchema;
  private String genericSchema;
  private String lawSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ACCOUNTING_ORG_ID, "Accounting Firm", "accounting-za");
    accountingSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();

    provisioningService.provisionTenant(GENERIC_ORG_ID, "Generic Org", null);
    genericSchema =
        orgSchemaMappingRepository.findByClerkOrgId(GENERIC_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(LAW_ORG_ID, "Law Firm", "law-za");
    lawSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LAW_ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void accountingTenantGetsUniversalAndAccountingPacks() {
    runInTenant(
        accountingSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);

                  // Post-Epic-462: common-customer pack deleted (all fields promoted to
                  // structural columns). Accounting tenant should still get accounting-za-customer
                  // pack with the remaining 8 non-promoted fields.
                  var commonFields =
                      customerFields.stream()
                          .filter(f -> "common-customer".equals(f.getPackId()))
                          .toList();
                  var accountingFields =
                      customerFields.stream()
                          .filter(f -> "accounting-za-customer".equals(f.getPackId()))
                          .toList();

                  assertThat(commonFields).isEmpty();
                  assertThat(accountingFields).hasSize(8);
                }));
  }

  @Test
  void genericTenantGetsOnlyUniversalPacks() {
    runInTenant(
        genericSchema,
        GENERIC_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);

                  // Post-Epic-462: common-customer pack deleted (all fields promoted to
                  // structural columns). Generic tenants should have no custom customer fields.
                  var commonFields =
                      customerFields.stream()
                          .filter(f -> "common-customer".equals(f.getPackId()))
                          .toList();
                  var accountingFields =
                      customerFields.stream()
                          .filter(f -> "accounting-za-customer".equals(f.getPackId()))
                          .toList();

                  assertThat(commonFields).isEmpty();
                  assertThat(accountingFields).isEmpty();
                }));
  }

  @Test
  void lawTenantDoesNotGetAccountingPacks() {
    runInTenant(
        lawSchema,
        LAW_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customerFields =
                      fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                          EntityType.CUSTOMER);

                  // Should have common-customer (universal) only — accounting packs skipped
                  var accountingFields =
                      customerFields.stream()
                          .filter(f -> "accounting-za-customer".equals(f.getPackId()))
                          .toList();

                  assertThat(accountingFields).isEmpty();
                }));
  }

  @Test
  void reSeedingDoesNotDuplicatePacks() {
    // Re-seed the accounting tenant
    fieldPackSeeder.seedPacksForTenant(accountingSchema, ACCOUNTING_ORG_ID);

    runInTenant(
        accountingSchema,
        ACCOUNTING_ORG_ID,
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

                  // Post-Epic-462: 8 remaining fields after promotion; should still be 8, not 16
                  assertThat(accountingFields).hasSize(8);
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
