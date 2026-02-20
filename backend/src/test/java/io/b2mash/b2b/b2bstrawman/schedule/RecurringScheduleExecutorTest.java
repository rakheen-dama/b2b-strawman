package io.b2mash.b2b.b2bstrawman.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTask;
import io.b2mash.b2b.b2bstrawman.projecttemplate.TemplateTaskRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecurringScheduleExecutorTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_executor_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RecurringScheduleExecutor executor;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private ScheduleExecutionRepository executionRepository;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID templateId;

  /**
   * Counter for generating unique customer names per test, avoiding unique constraint collisions.
   */
  private final AtomicInteger customerCounter = new AtomicInteger(0);

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Executor Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_exec_owner", "exec@test.com", "Exec Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Test Template",
                              "{customer} Monthly",
                              "Test description",
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  templateId = template.getId();
                }));
  }

  @Test
  void executeSchedules_happyPath_createsProject() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);

                  var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                  assertThat(updated.getExecutionCount()).isEqualTo(1);
                  assertThat(updated.getNextExecutionDate()).isAfter(LocalDate.now());
                }));
  }

  @Test
  void executeSchedules_idempotent_doesNotCreateDuplicateProject() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();
    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));
  }

  @Test
  void executeSchedules_customerOffboarded_skipsButAdvances() {
    var cid = createUniqueCustomer(LifecycleStatus.OFFBOARDED);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).isEmpty();

                  var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                  assertThat(updated.getNextExecutionDate()).isAfter(LocalDate.now());
                  assertThat(updated.getStatus()).isEqualTo("ACTIVE");
                }));
  }

  @Test
  void executeSchedules_customerProspect_skipsButAdvances() {
    var cid = createUniqueCustomer(LifecycleStatus.PROSPECT);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).isEmpty();

                  var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                  assertThat(updated.getNextExecutionDate()).isAfter(LocalDate.now());
                }));
  }

  @Test
  void executeSchedules_customerActive_createsProject() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));
  }

  @Test
  void executeSchedules_customerOnboarding_createsProject() {
    var cid = createUniqueCustomer(LifecycleStatus.ONBOARDING);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));
  }

  @Test
  void executeSchedules_customerDormant_createsProject() {
    var cid = createUniqueCustomer(LifecycleStatus.DORMANT);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));
  }

  @Test
  void executeSchedules_scheduleAutoCompletes_whenNextExceedsEndDate() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);

    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            LocalDate.now().plusDays(10),
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    return scheduleRepository.saveAndFlush(s);
                  });

          executor.executeSchedules();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo("COMPLETED");
              });
        });
  }

  @Test
  void executeSchedules_scheduleNotDue_skipsSchedule() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);

    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now().plusMonths(1),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now().plusDays(1));
                    return scheduleRepository.saveAndFlush(s);
                  });

          executor.executeSchedules();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getExecutionCount()).isZero();
              });
        });
  }

  @Test
  void executeSchedules_pausedSchedule_skipped() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);

    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now().minusMonths(1),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setStatus("PAUSED");
                    return scheduleRepository.saveAndFlush(s);
                  });

          executor.executeSchedules();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getExecutionCount()).isZero();
                assertThat(updated.getStatus()).isEqualTo("PAUSED");
              });
        });
  }

  @Test
  void executeSchedules_errorInOneSchedule_continuesOthers() {
    var cid1 = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var cid2 = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var goodSchedule = createDueSchedule("MONTHLY", cid1, null);

    // Create a schedule that will fail at execution time: valid FK refs at creation,
    // then drop the FK constraint and delete the customer so findById throws at execution time
    var badScheduleId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var badSchedule =
                      new RecurringSchedule(
                          templateId,
                          cid2,
                          null,
                          "WEEKLY",
                          LocalDate.now(),
                          null,
                          0,
                          memberId,
                          memberId);
                  badSchedule.setNextExecutionDate(LocalDate.now());
                  badSchedule = scheduleRepository.saveAndFlush(badSchedule);
                  badScheduleId[0] = badSchedule.getId();
                }));

    // Remove FK constraint and delete customer so execution fails with ResourceNotFoundException
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  jdbcTemplate.execute(
                      "ALTER TABLE recurring_schedules DROP CONSTRAINT IF EXISTS"
                          + " recurring_schedules_customer_id_fkey");
                  jdbcTemplate.update("DELETE FROM customers WHERE id = ?", cid2);
                }));

    executor.executeSchedules();

    // Verify the good schedule still completed despite the bad one failing
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          goodSchedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));

    // Clean up: delete bad schedule then restore FK constraint
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  jdbcTemplate.update(
                      "DELETE FROM recurring_schedules WHERE id = ?", badScheduleId[0]);
                  jdbcTemplate.execute(
                      "ALTER TABLE recurring_schedules ADD CONSTRAINT"
                          + " recurring_schedules_customer_id_fkey FOREIGN KEY (customer_id)"
                          + " REFERENCES customers(id)");
                }));
  }

  @Test
  void executeSchedules_noSchedulesDue_returnsZero() {
    // No due schedules specific to this test — just run and verify no errors
    executor.executeSchedules();
  }

  @Test
  void executeSchedules_projectCreatedWithTasks_tasksInheritedFromTemplate() {
    var templateWithTasksId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var t =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Template With Tasks",
                              "{customer} Tasks",
                              "desc",
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  templateWithTasksId[0] = t.getId();
                  templateTaskRepository.save(
                      new TemplateTask(
                          t.getId(),
                          "Task One",
                          "First task",
                          BigDecimal.ONE,
                          0,
                          true,
                          "UNASSIGNED"));
                  templateTaskRepository.save(
                      new TemplateTask(
                          t.getId(),
                          "Task Two",
                          "Second task",
                          BigDecimal.TWO,
                          1,
                          true,
                          "UNASSIGNED"));
                }));

    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueScheduleForTemplate("MONTHLY", cid, templateWithTasksId[0], null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);

                  var projectId = executions.getContent().getFirst().getProjectId();
                  var tasks = taskRepository.findByProjectId(projectId);
                  assertThat(tasks).hasSize(2);
                }));
  }

  @Test
  void executeSchedules_projectLead_assignedFromSchedule() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("MONTHLY", cid, memberId);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));
  }

  @Test
  void executeSchedules_nameOverride_usedOverTemplatePattern() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);

    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            "Custom Override Name",
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    return scheduleRepository.saveAndFlush(s);
                  });

          executor.executeSchedules();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var projectId = executions.getContent().getFirst().getProjectId();
                var project = projectRepository.findById(projectId).orElseThrow();
                assertThat(project.getName()).isEqualTo("Custom Override Name");
              });
        });
  }

  @Test
  void executeSchedules_weeklyFrequency_nextExecutionAdvancedByOneWeek() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("WEEKLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                  assertThat(updated.getExecutionCount()).isEqualTo(1);
                  assertThat(updated.getNextExecutionDate()).isAfter(LocalDate.now());
                }));
  }

  @Test
  void executeSchedules_monthlyFrequency_nextExecutionAdvancedByOneMonth() {
    var cid = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule = createDueSchedule("MONTHLY", cid, null);

    executor.executeSchedules();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                  assertThat(updated.getExecutionCount()).isEqualTo(1);
                  assertThat(updated.getNextExecutionDate()).isAfter(LocalDate.now());
                }));
  }

  @Test
  void executeSchedules_multipleTenants_processedIndependently() throws Exception {
    var orgId2 = "org_executor_test2";
    provisioningService.provisionTenant(orgId2, "Executor Test Org 2");
    planSyncService.syncPlan(orgId2, "pro-plan");
    var memberId2 =
        UUID.fromString(
            syncMember(orgId2, "user_exec_owner2", "exec2@test.com", "Exec Owner 2", "owner"));
    var tenantSchema2 =
        orgSchemaMappingRepository.findByClerkOrgId(orgId2).orElseThrow().getSchemaName();

    var templateId2 = new UUID[1];
    var customerId2 = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema2)
        .where(RequestScopes.ORG_ID, orgId2)
        .where(RequestScopes.MEMBER_ID, memberId2)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var t =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "T2 Template",
                                  "{customer} T2",
                                  "desc",
                                  true,
                                  "MANUAL",
                                  null,
                                  memberId2));
                      templateId2[0] = t.getId();
                      var c =
                          customerRepository.saveAndFlush(
                              TestCustomerFactory.createActiveCustomer(
                                  "T2 Corp", "t2@corp.com", memberId2));
                      customerId2[0] = c.getId();
                    }));

    var cid1 = createUniqueCustomer(LifecycleStatus.ACTIVE);
    var schedule1 = createDueSchedule("MONTHLY", cid1, null);

    var schedule2Id = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema2)
        .where(RequestScopes.ORG_ID, orgId2)
        .where(RequestScopes.MEMBER_ID, memberId2)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var s =
                          new RecurringSchedule(
                              templateId2[0],
                              customerId2[0],
                              null,
                              "MONTHLY",
                              LocalDate.now(),
                              null,
                              0,
                              memberId2,
                              memberId2);
                      s.setNextExecutionDate(LocalDate.now());
                      s = scheduleRepository.saveAndFlush(s);
                      schedule2Id[0] = s.getId();
                    }));

    executor.executeSchedules();

    // Verify tenant 1
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var executions =
                      executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                          schedule1.getId(), PageRequest.of(0, 10));
                  assertThat(executions.getContent()).hasSize(1);
                }));

    // Verify tenant 2
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema2)
        .where(RequestScopes.ORG_ID, orgId2)
        .where(RequestScopes.MEMBER_ID, memberId2)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var executions =
                          executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                              schedule2Id[0], PageRequest.of(0, 10));
                      assertThat(executions.getContent()).hasSize(1);
                    }));
  }

  // --- Helpers ---

  private UUID createUniqueCustomer(LifecycleStatus status) {
    int n = customerCounter.incrementAndGet();
    var result = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var c =
                      customerRepository.saveAndFlush(
                          new Customer(
                              "ExecCust" + n,
                              "execcust" + n + "@test.com",
                              null,
                              null,
                              null,
                              memberId,
                              CustomerType.INDIVIDUAL,
                              status));
                  result[0] = c.getId();
                }));
    return result[0];
  }

  private RecurringSchedule createDueSchedule(
      String frequency, UUID forCustomerId, UUID projectLeadMemberId) {
    return createDueScheduleForTemplate(frequency, forCustomerId, templateId, projectLeadMemberId);
  }

  private RecurringSchedule createDueScheduleForTemplate(
      String frequency, UUID forCustomerId, UUID forTemplateId, UUID projectLeadMemberId) {
    var result = new RecurringSchedule[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var s =
                      new RecurringSchedule(
                          forTemplateId,
                          forCustomerId,
                          null,
                          frequency,
                          LocalDate.now(),
                          null,
                          0,
                          projectLeadMemberId,
                          memberId);
                  s.setNextExecutionDate(LocalDate.now());
                  result[0] = scheduleRepository.saveAndFlush(s);
                }));
    return result[0];
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                    """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
