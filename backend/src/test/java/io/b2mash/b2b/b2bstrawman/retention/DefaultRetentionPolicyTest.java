package io.b2mash.b2b.b2bstrawman.retention;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultRetentionPolicyTest {

  private static final String ORG_ID = "org_drp_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private RetentionPolicyRepository retentionPolicyRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "DRP Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void provisioningCreatesDefaultRetentionPolicies() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var policies = retentionPolicyRepository.findAll();
                  assertThat(policies).hasSize(2);
                }));
  }

  @Test
  void defaultRetentionPoliciesHaveCorrectValues() {
    runInTenant(
        tenantSchema,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var policies = retentionPolicyRepository.findAll();

                  var customerPolicy =
                      policies.stream()
                          .filter(p -> "CUSTOMER".equals(p.getRecordType()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(customerPolicy.getRetentionDays()).isEqualTo(1825);
                  assertThat(customerPolicy.getTriggerEvent()).isEqualTo("CUSTOMER_OFFBOARDED");
                  assertThat(customerPolicy.getAction()).isEqualTo("FLAG");
                  assertThat(customerPolicy.isActive()).isTrue();

                  var auditPolicy =
                      policies.stream()
                          .filter(p -> "AUDIT_EVENT".equals(p.getRecordType()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(auditPolicy.getRetentionDays()).isEqualTo(2555);
                  assertThat(auditPolicy.getTriggerEvent()).isEqualTo("RECORD_CREATED");
                  assertThat(auditPolicy.getAction()).isEqualTo("FLAG");
                  assertThat(auditPolicy.isActive()).isTrue();
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
