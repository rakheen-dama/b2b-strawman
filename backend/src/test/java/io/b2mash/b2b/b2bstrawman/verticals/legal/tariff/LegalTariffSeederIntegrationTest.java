package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
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
class LegalTariffSeederIntegrationTest {

  private static final String LEGAL_ORG_ID = "org_lts_legal";
  private static final String ACCOUNTING_ORG_ID = "org_lts_accounting";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TariffScheduleRepository tariffScheduleRepository;
  @Autowired private LegalTariffSeeder legalTariffSeeder;
  @Autowired private TransactionTemplate transactionTemplate;

  private String legalSchema;
  private String accountingSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Law Firm", "legal-za");
    planSyncService.syncPlan(LEGAL_ORG_ID, "pro-plan");
    legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ACCOUNTING_ORG_ID, "Accounting Firm", "accounting-za");
    planSyncService.syncPlan(ACCOUNTING_ORG_ID, "pro-plan");
    accountingSchema =
        orgSchemaMappingRepository
            .findByClerkOrgId(ACCOUNTING_ORG_ID)
            .orElseThrow()
            .getSchemaName();
  }

  @Test
  void legalTenantGetsTariffScheduleWithItems() {
    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedule =
                      tariffScheduleRepository.findByNameAndIsSystemTrue(
                          "LSSA 2024/2025 High Court Party-and-Party");
                  assertThat(schedule).isPresent();
                  assertThat(schedule.get().isSystem()).isTrue();
                  assertThat(schedule.get().isActive()).isTrue();
                  assertThat(schedule.get().getCategory()).isEqualTo("PARTY_AND_PARTY");
                  assertThat(schedule.get().getCourtLevel()).isEqualTo("HIGH_COURT");

                  var withItems =
                      tariffScheduleRepository
                          .findWithItemsById(schedule.get().getId())
                          .orElseThrow();
                  assertThat(withItems.getItems()).hasSize(19);
                }));
  }

  @Test
  void tariffSeederIsIdempotent() {
    // Seed again — should not create a duplicate
    legalTariffSeeder.seedForTenant(legalSchema, LEGAL_ORG_ID);

    runInTenant(
        legalSchema,
        LEGAL_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedules = tariffScheduleRepository.findAll();
                  var systemSchedules =
                      schedules.stream().filter(TariffSchedule::isSystem).toList();
                  assertThat(systemSchedules).hasSize(1);
                }));
  }

  @Test
  void accountingTenantDoesNotGetTariffSchedules() {
    runInTenant(
        accountingSchema,
        ACCOUNTING_ORG_ID,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var schedules = tariffScheduleRepository.findAll();
                  assertThat(schedules).isEmpty();
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
