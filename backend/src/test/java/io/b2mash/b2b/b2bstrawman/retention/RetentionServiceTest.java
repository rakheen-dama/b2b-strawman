package io.b2mash.b2b.b2bstrawman.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionServiceTest {

  private static final String ORG_ID = "org_retention_svc_test";

  @Autowired private RetentionService retentionService;
  @Autowired private RetentionPolicyService retentionPolicyService;
  @Autowired private RetentionPolicyRepository policyRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Retention Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_retention_svc_test",
            "retention_svc@test.com",
            "Retention SVC Tester",
            null,
            "owner");
    memberId = syncResult.memberId();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void runCheck_noPolicies_returnsEmptyResult() {
    // Clean up any policies from other tests
    runInTenant(() -> policyRepository.deleteAll());

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    assertThat(result.getTotalFlagged()).isZero();
    assertThat(result.getFlagged()).isEmpty();
    assertThat(result.getCheckedAt()).isNotNull();
  }

  @Test
  void runCheck_customerPolicy_flagsOffboardedCustomersPastRetention() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    // Create an offboarded customer with offboardedAt 100 days ago
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  new Customer(
                      "Offboarded Client",
                      "offboarded-retention@test.com",
                      null,
                      null,
                      null,
                      memberId);
              // ACTIVE -> OFFBOARDING -> OFFBOARDED
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, memberId);
              customer.setOffboardedAt(Instant.now().minus(100, ChronoUnit.DAYS));
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // Create a 30-day retention policy for CUSTOMER
    runInTenant(() -> retentionPolicyService.create("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG"));

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    assertThat(result.getTotalFlagged()).isGreaterThanOrEqualTo(1);
    assertThat(result.getFlagged()).containsKey("CUSTOMER:CUSTOMER_OFFBOARDED");
    assertThat(result.getFlagged().get("CUSTOMER:CUSTOMER_OFFBOARDED").action()).isEqualTo("FLAG");
    assertThat(result.getFlagged().get("CUSTOMER:CUSTOMER_OFFBOARDED").recordIds())
        .contains(customerId);
  }

  @Test
  void runCheck_customerPolicy_doesNotFlagActiveCustomers() {
    // Clean up all policies and offboarded customers from prior tests
    runInTenant(
        () -> {
          policyRepository.deleteAll();
          customerRepository
              .findByLifecycleStatus(LifecycleStatus.OFFBOARDED)
              .forEach(c -> customerRepository.delete(c));
        });

    // Create an active customer
    runInTenant(
        () -> {
          var customer =
              new Customer(
                  "Active Client", "active-retention@test.com", null, null, null, memberId);
          customerRepository.save(customer);
        });

    runInTenant(() -> retentionPolicyService.create("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG"));

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    // Active customers should not appear since they are not OFFBOARDED
    assertThat(result.getFlagged()).doesNotContainKey("CUSTOMER:CUSTOMER_OFFBOARDED");
  }

  @Test
  void runCheck_auditEventPolicy_flagsOldEvents() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    // Audit events are created as part of other operations (e.g., retention check itself)
    // Create a policy with 0 days retention so all existing audit events are flagged
    runInTenant(
        () -> retentionPolicyService.create("AUDIT_EVENT", 0, "RECORD_CREATED", "ANONYMIZE"));

    // Run a first check to create at least one audit event
    runInTenant(() -> retentionService.runCheck());

    // Clean policies and re-create with a very short retention
    runInTenant(() -> policyRepository.deleteAll());
    runInTenant(
        () -> retentionPolicyService.create("AUDIT_EVENT", 0, "RECORD_CREATED", "ANONYMIZE"));

    // All existing audit events (occurred in the past) should be flagged with 0-day retention
    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    // There should be at least one audit event from the earlier runCheck
    assertThat(result.getFlagged().containsKey("AUDIT_EVENT:RECORD_CREATED")).isTrue();
    assertThat(result.getFlagged().get("AUDIT_EVENT:RECORD_CREATED").action())
        .isEqualTo("ANONYMIZE");
    assertThat(result.getFlagged().get("AUDIT_EVENT:RECORD_CREATED").count())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void runCheck_inactivePolicy_isIgnored() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    // Create a policy and immediately deactivate it
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("CUSTOMER", 1, "CUSTOMER_OFFBOARDED", "FLAG");
          policy.deactivate();
          policyRepository.save(policy);
        });

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    assertThat(result.getTotalFlagged()).isZero();
    assertThat(result.getFlagged()).isEmpty();
  }

  @Test
  void runCheck_cutoffCalculation_isCorrect() {
    // Clean up all policies and offboarded customers from prior tests
    runInTenant(
        () -> {
          policyRepository.deleteAll();
          customerRepository
              .findByLifecycleStatus(LifecycleStatus.OFFBOARDED)
              .forEach(c -> customerRepository.delete(c));
        });

    // Create a customer offboarded 10 days ago
    UUID recentCustomerId =
        runInTenant(
            () -> {
              var customer =
                  new Customer(
                      "Recently Offboarded",
                      "recent-offboarded@test.com",
                      null,
                      null,
                      null,
                      memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, memberId);
              customer.setOffboardedAt(Instant.now().minus(10, ChronoUnit.DAYS));
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // 30-day retention — customer offboarded 10 days ago should NOT be flagged
    runInTenant(() -> retentionPolicyService.create("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG"));

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    // With only a 10-day-old offboarded customer and 30-day retention, no customer should be
    // flagged
    assertThat(result.getFlagged()).doesNotContainKey("CUSTOMER:CUSTOMER_OFFBOARDED");
  }

  @Test
  void runCheck_publishesAuditEvent() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    long auditCountBefore = runInTenant(() -> auditEventRepository.count());

    runInTenant(() -> retentionService.runCheck());

    long auditCountAfter = runInTenant(() -> auditEventRepository.count());

    assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
  }

  @Test
  void crudCreate_persistsPolicy() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    RetentionPolicy policy =
        runInTenant(
            () -> retentionPolicyService.create("PROJECT", 365, "PROJECT_COMPLETED", "ANONYMIZE"));

    assertThat(policy.getId()).isNotNull();
    assertThat(policy.getRecordType()).isEqualTo("PROJECT");
    assertThat(policy.getRetentionDays()).isEqualTo(365);
    assertThat(policy.getTriggerEvent()).isEqualTo("PROJECT_COMPLETED");
    assertThat(policy.getAction()).isEqualTo("ANONYMIZE");
    assertThat(policy.isActive()).isTrue();
    assertThat(policy.getCreatedAt()).isNotNull();
  }

  @Test
  void crudCreate_uniquenessEnforced_throwsConflict() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    runInTenant(() -> retentionPolicyService.create("TIME_ENTRY", 90, "RECORD_CREATED", "FLAG"));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        retentionPolicyService.create(
                            "TIME_ENTRY", 180, "RECORD_CREATED", "ANONYMIZE")))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void crudUpdate_updatesRetentionDaysAndAction() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    RetentionPolicy created =
        runInTenant(() -> retentionPolicyService.create("COMMENT", 60, "RECORD_CREATED", "FLAG"));

    RetentionPolicy updated =
        runInTenant(() -> retentionPolicyService.update(created.getId(), 120, "ANONYMIZE"));

    assertThat(updated.getRetentionDays()).isEqualTo(120);
    assertThat(updated.getAction()).isEqualTo("ANONYMIZE");
    assertThat(updated.getUpdatedAt()).isAfter(created.getUpdatedAt());
  }

  @Test
  void crudDelete_removesPolicy() {
    // Clean up
    runInTenant(() -> policyRepository.deleteAll());

    RetentionPolicy created =
        runInTenant(
            () ->
                retentionPolicyService.create("NOTIFICATION", 180, "RECORD_CREATED", "ANONYMIZE"));
    UUID policyId = created.getId();

    runInTenant(() -> retentionPolicyService.delete(policyId));

    boolean stillExists = runInTenant(() -> policyRepository.existsById(policyId));
    assertThat(stillExists).isFalse();
  }

  @Test
  void crudDelete_notFound_throwsException() {
    assertThatThrownBy(() -> runInTenant(() -> retentionPolicyService.delete(UUID.randomUUID())))
        .isInstanceOf(io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException.class);
  }

  @Test
  void crudCreate_negativeRetentionDays_throwsIllegalArgument() {
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> retentionPolicyService.create("TASK", -1, "RECORD_CREATED", "FLAG")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retentionDays");
  }

  @Test
  void crudCreate_blankRecordType_throwsIllegalArgument() {
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> retentionPolicyService.create("  ", 30, "RECORD_CREATED", "FLAG")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordType");
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_retention_svc_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }

  private void runInTenant(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
