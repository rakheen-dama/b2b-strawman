package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetainerConsumptionListenerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_consumption_test_124a";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private NotificationRepository notificationRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Consumption Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_cons_owner_124a", "owner124a@test.com", "Owner 124A", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  /** Test data holder for a customer + project + task + retainer setup. */
  private record TestSetup(
      UUID customerId, UUID projectId, UUID taskId, UUID agreementId, UUID periodId) {}

  private TestSetup createHourBankSetup(
      String nameSuffix, BigDecimal allocatedHours, LocalDate periodStart) {
    var ref = new AtomicReference<TestSetup>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(
                          "Customer " + nameSuffix,
                          nameSuffix + "@test.com",
                          null,
                          null,
                          null,
                          memberId);
                  customer = customerRepository.save(customer);
                  UUID cid = customer.getId();

                  var project = new Project("Project " + nameSuffix, null, memberId);
                  project = projectRepository.save(project);
                  UUID pid = project.getId();

                  customerProjectRepository.save(new CustomerProject(cid, pid, memberId));

                  var task = new Task(pid, "Task " + nameSuffix, null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  UUID tid = task.getId();

                  var req =
                      new CreateRetainerRequest(
                          cid,
                          null,
                          "Retainer " + nameSuffix,
                          RetainerType.HOUR_BANK,
                          RetainerFrequency.MONTHLY,
                          periodStart,
                          null,
                          allocatedHours,
                          new BigDecimal("5000"),
                          RolloverPolicy.FORFEIT,
                          null,
                          null);
                  var retainerResp = retainerAgreementService.createRetainer(req, memberId);
                  UUID aid = retainerResp.id();
                  UUID peid = retainerResp.currentPeriod().id();

                  ref.set(new TestSetup(cid, pid, tid, aid, peid));
                }));
    return ref.get();
  }

  @Test
  void createTimeEntry_billable_updatesRetainerConsumption() {
    var setup =
        createHourBankSetup("billable-update", new BigDecimal("60"), LocalDate.of(2026, 3, 1));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      setup.taskId(),
                      LocalDate.of(2026, 3, 15),
                      120,
                      true,
                      null,
                      "Work done",
                      memberId,
                      "owner");
                }));

    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("2.00");
          assertThat(period.getRemainingHours()).isEqualByComparingTo("58.00");
        });
  }

  @Test
  void createTimeEntry_nonBillable_doesNotUpdateRetainerConsumption() {
    var setup = createHourBankSetup("non-billable", new BigDecimal("40"), LocalDate.of(2026, 3, 1));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      setup.taskId(),
                      LocalDate.of(2026, 3, 10),
                      120,
                      false,
                      null,
                      "Non-billable work",
                      memberId,
                      "owner");
                }));

    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("0.00");
          assertThat(period.getRemainingHours()).isEqualByComparingTo("40.00");
        });
  }

  @Test
  void updateTimeEntry_changeDuration_updatesConsumption() {
    var setup =
        createHourBankSetup("update-duration", new BigDecimal("40"), LocalDate.of(2026, 3, 1));

    var entryRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var entry =
                      timeEntryService.createTimeEntry(
                          setup.taskId(),
                          LocalDate.of(2026, 3, 10),
                          60,
                          true,
                          null,
                          "Initial work",
                          memberId,
                          "owner");
                  entryRef.set(entry.getId());
                }));

    // Update to 180 min = 3 hours
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.updateTimeEntry(
                      entryRef.get(), null, 180, null, null, null, memberId, "owner");
                }));

    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("3.00");
          assertThat(period.getRemainingHours()).isEqualByComparingTo("37.00");
        });
  }

  @Test
  void deleteTimeEntry_updatesRetainerConsumption() {
    var setup = createHourBankSetup("delete-entry", new BigDecimal("40"), LocalDate.of(2026, 3, 1));

    var entryRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var entry =
                      timeEntryService.createTimeEntry(
                          setup.taskId(),
                          LocalDate.of(2026, 3, 5),
                          120,
                          true,
                          null,
                          "Entry to delete",
                          memberId,
                          "owner");
                  entryRef.set(entry.getId());
                }));

    // Verify consumption = 2h after create
    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("2.00");
        });

    // Delete the entry
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.deleteTimeEntry(entryRef.get(), memberId, "owner");
                }));

    // Verify consumption back to 0
    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("0.00");
          assertThat(period.getRemainingHours()).isEqualByComparingTo("40.00");
        });
  }

  @Test
  void timeEntry_projectNotLinkedToCustomer_noConsumptionUpdate() {
    var setup = createHourBankSetup("no-link", new BigDecimal("40"), LocalDate.of(2026, 3, 1));

    var unlinkedTaskRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project("Unlinked Project", null, memberId);
                  project = projectRepository.save(project);

                  var task =
                      new Task(project.getId(), "Unlinked Task", null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  unlinkedTaskRef.set(task.getId());
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      unlinkedTaskRef.get(),
                      LocalDate.of(2026, 3, 10),
                      120,
                      true,
                      null,
                      "Unlinked work",
                      memberId,
                      "owner");
                }));

    // The original retainer's period should not be affected
    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("0.00");
        });
  }

  @Test
  void timeEntry_customerHasNoRetainer_noConsumptionUpdate() {
    var taskRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(
                          "NoRetainer Corp", "noretainer@test.com", null, null, null, memberId);
                  customer = customerRepository.save(customer);
                  UUID cid = customer.getId();

                  var project = new Project("NoRetainer Project", null, memberId);
                  project = projectRepository.save(project);

                  customerProjectRepository.save(
                      new CustomerProject(cid, project.getId(), memberId));

                  var task =
                      new Task(
                          project.getId(), "NoRetainer Task", null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  taskRef.set(task.getId());
                }));

    // Should not throw — listener silently returns
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      taskRef.get(),
                      LocalDate.of(2026, 3, 10),
                      60,
                      true,
                      null,
                      "Work",
                      memberId,
                      "owner");
                }));
  }

  @Test
  void timeEntry_noOpenPeriod_noConsumptionUpdate() {
    var setup =
        createHourBankSetup("no-open-period", new BigDecimal("40"), LocalDate.of(2026, 3, 1));

    // Manually close the period
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var period = retainerPeriodRepository.findById(setup.periodId()).orElseThrow();
                  period.close(null, memberId, BigDecimal.ZERO, BigDecimal.ZERO);
                }));

    // Creating a time entry should be a no-op for retainer consumption
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      setup.taskId(),
                      LocalDate.of(2026, 3, 10),
                      60,
                      true,
                      null,
                      "Work",
                      memberId,
                      "owner");
                }));
  }

  @Test
  void consumption_crosses80pct_sendsApproachingCapacityNotification() {
    var setup =
        createHourBankSetup("threshold-80pct", new BigDecimal("10"), LocalDate.of(2026, 3, 1));

    // Log 8.5 hours = 510 minutes (85% of 10h)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      setup.taskId(),
                      LocalDate.of(2026, 3, 10),
                      510,
                      true,
                      null,
                      "Lot of work",
                      memberId,
                      "owner");
                }));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_APPROACHING_CAPACITY".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer threshold-80pct"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          assertThat(notifications.getFirst().getTitle()).contains("Customer threshold-80pct");
        });
  }

  @Test
  void consumption_crosses100pct_sendsFullyConsumedNotification() {
    var setup =
        createHourBankSetup("threshold-100pct", new BigDecimal("5"), LocalDate.of(2026, 3, 1));

    // 310 min = 5.17 hours > 5h allocated = over 100%
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      setup.taskId(),
                      LocalDate.of(2026, 3, 10),
                      310,
                      true,
                      null,
                      "Overrun work",
                      memberId,
                      "owner");
                }));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_FULLY_CONSUMED".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer threshold-100pct"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          assertThat(notifications.getFirst().getTitle()).contains("fully consumed");
        });
  }

  @Test
  void fixedFeeRetainer_updatesConsumedHours_noThresholdNotification() {
    var periodRef = new AtomicReference<UUID>();
    var taskRef = new AtomicReference<UUID>();
    var agreementRef = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer("FixedFee 124A", "ff124a@test.com", null, null, null, memberId);
                  customer = customerRepository.save(customer);
                  UUID cid = customer.getId();

                  var project = new Project("FF Project 124A", null, memberId);
                  project = projectRepository.save(project);
                  customerProjectRepository.save(
                      new CustomerProject(cid, project.getId(), memberId));

                  var task =
                      new Task(project.getId(), "FF Task 124A", null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  taskRef.set(task.getId());

                  var req =
                      new CreateRetainerRequest(
                          cid,
                          null,
                          "FF Retainer 124A",
                          RetainerType.FIXED_FEE,
                          RetainerFrequency.MONTHLY,
                          LocalDate.of(2026, 3, 1),
                          null,
                          null,
                          new BigDecimal("5000"),
                          RolloverPolicy.FORFEIT,
                          null,
                          null);
                  var resp = retainerAgreementService.createRetainer(req, memberId);
                  periodRef.set(resp.currentPeriod().id());
                  agreementRef.set(resp.id());
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      taskRef.get(),
                      LocalDate.of(2026, 3, 5),
                      120,
                      true,
                      null,
                      "FF Work",
                      memberId,
                      "owner");
                }));

    runInTenant(
        () -> {
          var period = retainerPeriodRepository.findById(periodRef.get()).orElseThrow();
          assertThat(period.getConsumedHours()).isEqualByComparingTo("2.00");

          // No threshold notifications for FIXED_FEE retainer
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(
                      n ->
                          "RETAINER_APPROACHING_CAPACITY".equals(n.getType())
                              || "RETAINER_FULLY_CONSUMED".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("FixedFee 124A"))
                  .toList();
          assertThat(notifications).isEmpty();
        });
  }
}
