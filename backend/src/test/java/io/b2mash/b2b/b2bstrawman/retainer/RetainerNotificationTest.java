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
class RetainerNotificationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_notif_test_128b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private RetainerPeriodService retainerPeriodService;
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
    provisioningService.provisionTenant(ORG_ID, "Retainer Notification Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_notif_owner_128b", "owner128b@test.com", "Owner 128B", "owner"));
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
  void readyToClose_sendsNotificationForOverduePeriod() {
    // Create a retainer with a period that started in the past (period end already passed)
    var setup =
        createHourBankSetup("ready-to-close", new BigDecimal("20"), LocalDate.of(2025, 12, 1));

    // The period end will be ~2026-01-01 (monthly), which is in the past
    // Calling checkAndNotifyReadyToClose should detect this and send notification
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> retainerPeriodService.checkAndNotifyReadyToClose()));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_PERIOD_READY_TO_CLOSE".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer ready-to-close"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          assertThat(notifications.getFirst().getTitle()).contains("ready to close");
          assertThat(notifications.getFirst().getBody()).contains("Retainer ready-to-close");
        });
  }

  @Test
  void readyToClose_deduplicatesNotifications() {
    // Create a retainer with past period
    var setup = createHourBankSetup("dedup-test", new BigDecimal("20"), LocalDate.of(2025, 11, 1));

    // Call twice — should only create one notification per period
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> retainerPeriodService.checkAndNotifyReadyToClose()));
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> retainerPeriodService.checkAndNotifyReadyToClose()));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_PERIOD_READY_TO_CLOSE".equals(n.getType()))
                  .filter(n -> n.getReferenceEntityId().equals(setup.periodId()))
                  .toList();
          // Should have exactly 1 notification per admin/owner (we have 1 owner)
          assertThat(notifications).hasSize(1);
        });
  }

  @Test
  void approachingCapacity_sendsNotificationWithBody() {
    var setup = createHourBankSetup("notif-80pct", new BigDecimal("10"), LocalDate.of(2026, 4, 1));

    // Log 8.5 hours = 510 minutes (85% of 10h) — crosses 80% threshold
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 4, 10),
                        510,
                        true,
                        null,
                        "Threshold work",
                        memberId,
                        "owner")));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_APPROACHING_CAPACITY".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer notif-80pct"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          var notification = notifications.getFirst();
          assertThat(notification.getTitle()).contains("capacity");
          assertThat(notification.getBody()).contains("Allocated:");
          assertThat(notification.getBody()).contains("Consumed:");
          assertThat(notification.getBody()).contains("Remaining:");
        });
  }

  @Test
  void fullyConsumed_sendsNotificationWithBody() {
    var setup = createHourBankSetup("notif-100pct", new BigDecimal("5"), LocalDate.of(2026, 4, 1));

    // 310 min = 5.17 hours > 5h allocated = over 100%
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 4, 10),
                        310,
                        true,
                        null,
                        "Overrun work",
                        memberId,
                        "owner")));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_FULLY_CONSUMED".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer notif-100pct"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          var notification = notifications.getFirst();
          assertThat(notification.getTitle()).contains("fully consumed");
          assertThat(notification.getBody()).contains("Allocated:");
          assertThat(notification.getBody()).contains("Consumed:");
        });
  }

  @Test
  void manualTerminate_sendsTerminatedNotification() {
    var setup =
        createHourBankSetup("terminate-notif", new BigDecimal("20"), LocalDate.of(2026, 5, 1));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> retainerAgreementService.terminateRetainer(setup.agreementId(), memberId)));

    runInTenant(
        () -> {
          var notifications =
              notificationRepository.findAll().stream()
                  .filter(n -> "RETAINER_TERMINATED".equals(n.getType()))
                  .filter(n -> n.getTitle().contains("Customer terminate-notif"))
                  .toList();
          assertThat(notifications).isNotEmpty();
          assertThat(notifications.getFirst().getTitle()).contains("has been terminated");
          assertThat(notifications.getFirst().getReferenceEntityId())
              .isEqualTo(setup.agreementId());
        });
  }
}
