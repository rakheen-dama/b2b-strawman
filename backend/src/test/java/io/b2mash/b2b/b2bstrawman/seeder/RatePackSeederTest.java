package io.b2mash.b2b.b2bstrawman.seeder;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RatePackSeederTest {

  private static final String ORG_ID = "org_rate_pack_seeder_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private RatePackSeeder ratePackSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Rate Pack Seeder Test Org", "accounting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  @Order(1)
  void seedsFourOrgLevelBillingRates() {
    ratePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Seeded rates have null memberId — filter to find them
                  var seededRates =
                      billingRateRepository.findAll().stream()
                          .filter(r -> r.getMemberId() == null)
                          .toList();
                  assertThat(seededRates).hasSize(4);
                  assertThat(seededRates)
                      .allSatisfy(
                          rate -> {
                            assertThat(rate.getMemberId()).isNull();
                            assertThat(rate.getProjectId()).isNull();
                            assertThat(rate.getCustomerId()).isNull();
                            assertThat(rate.getCurrency()).isEqualTo("ZAR");
                          });
                }));
  }

  @Test
  @Order(2)
  void idempotentSecondRunDoesNotDuplicateRates() {
    ratePackSeeder.seedPacksForTenant(tenantSchema, ORG_ID);
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seededRates =
                      billingRateRepository.findAll().stream()
                          .filter(r -> r.getMemberId() == null)
                          .toList();
                  assertThat(seededRates).hasSize(4); // still 4, not 8
                }));
  }

  @Test
  @Order(3)
  void nonMatchingVerticalProfileSkipsPack() {
    String nonMatchingOrgId = "org_rate_pack_no_match";
    provisioningService.provisionTenant(nonMatchingOrgId, "No Match Org", null);
    var schema =
        orgSchemaMappingRepository.findByClerkOrgId(nonMatchingOrgId).orElseThrow().getSchemaName();

    ratePackSeeder.seedPacksForTenant(schema, nonMatchingOrgId);
    runInTenant(
        schema,
        nonMatchingOrgId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seededRates =
                      billingRateRepository.findAll().stream()
                          .filter(r -> r.getMemberId() == null)
                          .toList();
                  assertThat(seededRates).isEmpty();
                }));
  }

  private void runInTenant(String schema, Runnable action) {
    runInTenant(schema, ORG_ID, action);
  }

  private void runInTenant(String schema, String orgId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
