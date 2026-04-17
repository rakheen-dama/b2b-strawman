package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
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
 * Integration tests for the {@code consulting-za} rate pack: asserts the pack JSON is discovered by
 * {@link io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder} and applied to a tenant provisioned with
 * the {@code consulting-za} vertical profile. All 8 agency roles should seed as org-level {@link
 * BillingRate} rows denominated in ZAR.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsultingZaRatePackTest {

  private static final String ORG_ID = "org_cz_rate_pack_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(
        ORG_ID, "Consulting ZA Rate Pack Test Org", "consulting-za");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void seedsEightAgencyBillingRates() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seededRates = seededOrgLevelRates();
                  assertThat(seededRates).hasSize(8);
                }));
  }

  @Test
  void allRatesAreOrgLevelAndInZar() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seededRates = seededOrgLevelRates();
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
  void creativeDirectorRateIs1800Zar() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var seededRates = seededOrgLevelRates();
                  var hourlyRates = seededRates.stream().map(BillingRate::getHourlyRate).toList();
                  // Creative Director (top of the 8-role hierarchy) should be the top rate.
                  assertThat(hourlyRates)
                      .anySatisfy(
                          r -> assertThat(r).isEqualByComparingTo(new BigDecimal("1800.0")));
                  // Producer / Junior is the lowest rate.
                  assertThat(hourlyRates)
                      .anySatisfy(r -> assertThat(r).isEqualByComparingTo(new BigDecimal("600.0")));
                }));
  }

  private List<BillingRate> seededOrgLevelRates() {
    return billingRateRepository.findAll().stream()
        .filter(r -> r.getMemberId() == null)
        .filter(r -> r.getProjectId() == null)
        .filter(r -> r.getCustomerId() == null)
        .toList();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
