package io.b2mash.b2b.b2bstrawman.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
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
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private NotificationRepository notificationRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID testTaskId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Retention Service Test Org", null);
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

    // Create a project and task for time entry tests
    testTaskId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.ORG_ID, ORG_ID)
            .where(RequestScopes.MEMBER_ID, memberId)
            .call(
                () ->
                    transactionTemplate.execute(
                        tx -> {
                          var project =
                              new Project(
                                  "Retention Test Project", "For time entry tests", memberId);
                          project = projectRepository.save(project);
                          var task =
                              new Task(
                                  project.getId(),
                                  "Retention Test Task",
                                  null,
                                  "MEDIUM",
                                  "TASK",
                                  null,
                                  memberId);
                          task = taskRepository.save(task);
                          return task.getId();
                        }));
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
                  TestCustomerFactory.createActiveCustomer(
                      "Offboarded Client", "offboarded-retention@test.com", memberId);
              // ACTIVE -> OFFBOARDING -> OFFBOARDED
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, memberId);
              customer.setOffboardedAt(Instant.now().minus(100, ChronoUnit.DAYS));
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // Create a 30-day retention policy for CUSTOMER (bypass financial minimum via raw save)
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG");
          policyRepository.save(policy);
        });

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
              TestCustomerFactory.createActiveCustomer(
                  "Active Client", "active-retention@test.com", memberId);
          customerRepository.save(customer);
        });

    // Bypass financial minimum via raw save (we're testing retention check logic, not creation)
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG");
          policyRepository.save(policy);
        });

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
                  TestCustomerFactory.createActiveCustomer(
                      "Recently Offboarded", "recent-offboarded@test.com", memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDING, memberId);
              customer.transitionLifecycleStatus(LifecycleStatus.OFFBOARDED, memberId);
              customer.setOffboardedAt(Instant.now().minus(10, ChronoUnit.DAYS));
              customer = customerRepository.save(customer);
              return customer.getId();
            });

    // 30-day retention — customer offboarded 10 days ago should NOT be flagged
    // Bypass financial minimum via raw save (testing cutoff logic)
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("CUSTOMER", 30, "CUSTOMER_OFFBOARDED", "FLAG");
          policyRepository.save(policy);
        });

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

    runInTenant(() -> retentionPolicyService.create("TIME_ENTRY", 1800, "RECORD_CREATED", "FLAG"));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        retentionPolicyService.create(
                            "TIME_ENTRY", 1800, "RECORD_CREATED", "ANONYMIZE")))
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

  // --- Epic 376A: New test methods ---

  @Test
  void runCheck_timeEntryPolicy_flagsPastRetention() {
    runInTenant(
        () -> {
          policyRepository.deleteAll();
          timeEntryRepository.deleteAll();
        });

    // Create a time entry 60 days old
    UUID timeEntryId =
        runInTenant(
            () -> {
              var te =
                  new TimeEntry(
                      testTaskId,
                      memberId,
                      LocalDate.now().minusDays(60),
                      120,
                      true,
                      null,
                      "Old work");
              te = timeEntryRepository.save(te);
              return te.getId();
            });

    // Create a 30-day retention policy for TIME_ENTRY (bypass financial minimum via raw save)
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("TIME_ENTRY", 30, "RECORD_CREATED", "delete");
          policyRepository.save(policy);
        });

    RetentionCheckResult result = runInTenant(() -> retentionService.runCheck());

    assertThat(result.getFlagged()).containsKey("TIME_ENTRY:RECORD_CREATED");
    assertThat(result.getFlagged().get("TIME_ENTRY:RECORD_CREATED").recordIds())
        .contains(timeEntryId);
  }

  @Test
  void runCheck_warningNotificationSent_forApproachingDeadline() {
    runInTenant(
        () -> {
          policyRepository.deleteAll();
          timeEntryRepository.deleteAll();
        });

    // Create a time entry 45 days old — with a 60-day policy, this is past the warn cutoff
    // (60 - 30 = 30 days) but not past the expired cutoff (60 days)
    runInTenant(
        () -> {
          var te =
              new TimeEntry(
                  testTaskId,
                  memberId,
                  LocalDate.now().minusDays(45),
                  60,
                  false,
                  null,
                  "Approaching retention");
          timeEntryRepository.save(te);
        });

    // Create a policy directly with 60-day retention (bypassing financial minimum validation)
    runInTenant(
        () -> {
          var policy = new RetentionPolicy("TIME_ENTRY", 60, "RECORD_CREATED", "delete");
          policyRepository.save(policy);
        });

    runInTenant(() -> retentionService.runCheck());

    // Verify a RETENTION_PURGE_WARNING notification was created for the owner
    boolean warningExists =
        runInTenant(
            () ->
                notificationRepository.existsByTypeAndRecipientMemberIdAndCreatedAtAfter(
                    "RETENTION_PURGE_WARNING",
                    memberId,
                    Instant.now().minus(10, ChronoUnit.SECONDS)));

    assertThat(warningExists).isTrue();
  }

  @Test
  void financialMinimumCheck_throwsForPolicyBelowMinimum() {
    runInTenant(() -> policyRepository.deleteAll());

    // Attempt to create a CUSTOMER policy with only 100 days (below 1800-day minimum)
    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        retentionPolicyService.create(
                            "CUSTOMER", 100, "RECORD_CREATED", "anonymize")))
        .isInstanceOf(InvalidStateException.class);

    // Also verify update path: create with valid days, then try to update below minimum
    runInTenant(() -> policyRepository.deleteAll());
    RetentionPolicy created =
        runInTenant(
            () -> retentionPolicyService.create("TIME_ENTRY", 1800, "RECORD_CREATED", "delete"));

    assertThatThrownBy(
            () -> runInTenant(() -> retentionPolicyService.update(created.getId(), 100, "delete")))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void runCheck_lastEvaluatedAt_isUpdatedAfterEvaluation() {
    runInTenant(() -> policyRepository.deleteAll());

    // Create any active policy
    RetentionPolicy created =
        runInTenant(
            () -> {
              var policy = new RetentionPolicy("COMMENT", 90, "RECORD_CREATED", "delete");
              return policyRepository.save(policy);
            });

    assertThat(created.getLastEvaluatedAt()).isNull();

    // Run check
    runInTenant(() -> retentionService.runCheck());

    // Reload the policy and verify lastEvaluatedAt is set
    RetentionPolicy reloaded =
        runInTenant(() -> policyRepository.findById(created.getId()).orElseThrow());

    assertThat(reloaded.getLastEvaluatedAt()).isNotNull();
    assertThat(reloaded.getLastEvaluatedAt()).isAfter(Instant.now().minus(5, ChronoUnit.SECONDS));
  }

  @Test
  void seedJurisdictionDefaults_createsAndIsIdempotent() {
    runInTenant(() -> policyRepository.deleteAll());

    // Seed ZA defaults
    runInTenant(() -> retentionService.seedJurisdictionDefaults("ZA"));

    long count = runInTenant(() -> policyRepository.findAll().size());
    assertThat(count).isEqualTo(5);

    // Verify specific policies exist
    assertThat(
            runInTenant(
                () ->
                    policyRepository.existsByRecordTypeAndTriggerEvent(
                        "CUSTOMER", "CUSTOMER_OFFBOARDED")))
        .isTrue();
    assertThat(
            runInTenant(
                () ->
                    policyRepository.existsByRecordTypeAndTriggerEvent(
                        "TIME_ENTRY", "RECORD_CREATED")))
        .isTrue();
    assertThat(
            runInTenant(
                () ->
                    policyRepository.existsByRecordTypeAndTriggerEvent(
                        "DOCUMENT", "RECORD_CREATED")))
        .isTrue();
    assertThat(
            runInTenant(
                () ->
                    policyRepository.existsByRecordTypeAndTriggerEvent(
                        "COMMENT", "RECORD_CREATED")))
        .isTrue();
    assertThat(
            runInTenant(
                () ->
                    policyRepository.existsByRecordTypeAndTriggerEvent(
                        "AUDIT_EVENT", "RECORD_CREATED")))
        .isTrue();

    // Call again — should be idempotent
    runInTenant(() -> retentionService.seedJurisdictionDefaults("ZA"));

    long countAfter = runInTenant(() -> policyRepository.findAll().size());
    assertThat(countAfter).isEqualTo(5);
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken("user_retention_svc_test", null, Collections.emptyList());
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
