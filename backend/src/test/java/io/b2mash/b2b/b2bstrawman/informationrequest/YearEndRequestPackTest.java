package io.b2mash.b2b.b2bstrawman.informationrequest;

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
class YearEndRequestPackTest {

  private static final String ORG_ID = "org_yerp_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private RequestTemplateRepository requestTemplateRepository;
  @Autowired private RequestTemplateItemRepository requestTemplateItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Year-End Request Pack Test Org", "accounting-za");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void accountingTenantGets8ItemsFromYearEndRequestPack() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates =
                      requestTemplateRepository.findByPackId("year-end-info-request-za");
                  assertThat(templates).hasSize(1);
                  var template = templates.getFirst();
                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());
                  assertThat(items).hasSize(8);
                }));
  }

  @Test
  void requiredFlagsCorrect() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates =
                      requestTemplateRepository.findByPackId("year-end-info-request-za");
                  var template = templates.getFirst();
                  var items =
                      requestTemplateItemRepository.findByTemplateIdOrderBySortOrder(
                          template.getId());

                  // Items 1-4 and 8 are required
                  assertThat(items.get(0).isRequired()).isTrue(); // Trial Balance
                  assertThat(items.get(1).isRequired()).isTrue(); // Bank Statements
                  assertThat(items.get(2).isRequired()).isTrue(); // Loan Agreements
                  assertThat(items.get(3).isRequired()).isTrue(); // Fixed Asset Register
                  assertThat(items.get(7).isRequired()).isTrue(); // Payroll Summary

                  // Items 5-7 are not required
                  assertThat(items.get(4).isRequired()).isFalse(); // Debtors Age Analysis
                  assertThat(items.get(5).isRequired()).isFalse(); // Creditors Age Analysis
                  assertThat(items.get(6).isRequired()).isFalse(); // Insurance Schedule
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
