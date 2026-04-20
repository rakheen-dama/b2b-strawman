package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerConsumptionEntryRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerSummaryRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementService;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerPeriodRolloverEvent;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.settings.PortalRetainerMemberDisplay;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link RetainerPortalSyncService}. Exercises the full event-listener +
 * read-model stack on embedded Postgres (no mocks). Covers the 5 scenarios in the Epic 496A test
 * plan:
 *
 * <ol>
 *   <li>RetainerAgreementCreated → summary row with status=ACTIVE, hours_consumed=0.
 *   <li>TimeEntryChanged on retainer-backed project → consumption entry + summary hours updated.
 *   <li>RetainerPeriodRollover → summary rolled to new period with fresh counters.
 *   <li>FIRST_NAME_ROLE mode → consumption entry member_display_name = "First (Role)".
 *   <li>Member-display mode change re-resolves on new events; existing entries unchanged.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetainerPortalSyncServiceIntegrationTest {

  private static final String ORG_ID = "org_portal_retainer_sync_test";
  private static final String ORG_NAME = "Portal Retainer Sync Test Org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private PortalRetainerSummaryRepository summaryRepo;
  @Autowired private PortalRetainerConsumptionEntryRepository entryRepo;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_retainer_sync_owner",
                "retainer_sync_owner@test.com",
                "Alice Ndlovu",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  // ==========================================================================
  // Scenario 1 — RetainerAgreementCreatedEvent seeds a summary row
  // ==========================================================================

  @Test
  void retainerCreated_upsertsSummaryRowWithActiveStatusAndZeroConsumption() {
    var setup = createHourBankSetup("created", new BigDecimal("40"), LocalDate.of(2026, 4, 1));

    // createRetainer publishes RetainerAgreementCreatedEvent inside its @Transactional — the
    // AFTER_COMMIT sync listener fires after the outer transactionTemplate commits. Read the
    // portal row now.
    var summaries = summaryRepo.findByCustomerId(setup.customerId());
    assertThat(summaries).hasSize(1);
    var summary = summaries.getFirst();
    assertThat(summary.id()).isEqualTo(setup.agreementId());
    assertThat(summary.customerId()).isEqualTo(setup.customerId());
    assertThat(summary.name()).isEqualTo("Retainer created");
    assertThat(summary.status()).isEqualTo("ACTIVE");
    assertThat(summary.hoursAllotted()).isEqualByComparingTo("40");
    assertThat(summary.hoursConsumed()).isEqualByComparingTo("0");
    assertThat(summary.hoursRemaining()).isEqualByComparingTo("40");
    assertThat(summary.periodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(summary.rolloverHours()).isEqualByComparingTo("0");
  }

  // ==========================================================================
  // Scenario 2 — TimeEntryChanged on retainer-backed project → consumption +
  //              summary counters updated
  // ==========================================================================

  @Test
  void timeEntryChanged_insertsConsumptionRowAndUpdatesSummaryHours() {
    var setup = createHourBankSetup("consumed", new BigDecimal("20"), LocalDate.of(2026, 5, 1));

    // Log 90 minutes = 1.50 hours against the retainer-backed task.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 5, 10),
                        90,
                        true,
                        null,
                        "Research call",
                        new ActorContext(memberId, "owner"))));

    // One consumption row with the correct hours.
    var entries =
        entryRepo.findByRetainerIdAndEntryDateRange(
            setup.customerId(), setup.agreementId(), null, null);
    assertThat(entries).hasSize(1);
    var entry = entries.getFirst();
    assertThat(entry.customerId()).isEqualTo(setup.customerId());
    assertThat(entry.retainerId()).isEqualTo(setup.agreementId());
    assertThat(entry.hours()).isEqualByComparingTo("1.50");
    assertThat(entry.occurredAt()).isEqualTo(LocalDate.of(2026, 5, 10));
    assertThat(entry.description()).isEqualTo("Research call");

    // Summary row shows the consumed hours reduced the remaining balance.
    var summary =
        summaryRepo
            .findByCustomerIdAndRetainerId(setup.customerId(), setup.agreementId())
            .orElseThrow();
    assertThat(summary.hoursConsumed()).isEqualByComparingTo("1.50");
    assertThat(summary.hoursRemaining()).isEqualByComparingTo("18.50");
  }

  // ==========================================================================
  // Scenario 3 — RetainerPeriodRolloverEvent refreshes summary to new period
  // ==========================================================================

  @Test
  void periodRollover_refreshesSummaryWithNewPeriodAndResetCounters() {
    var setup = createHourBankSetup("rollover", new BigDecimal("30"), LocalDate.of(2026, 6, 1));

    // Publish a rollover event directly — closePeriod() has too many preconditions to exercise
    // from here (past period end, invoice, billing rate, etc). The sync listener's contract is
    // simple: given a rollover event, stamp the summary's new period bounds and reset counters.
    LocalDate newStart = LocalDate.of(2026, 7, 1);
    LocalDate newEnd = LocalDate.of(2026, 8, 1);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    eventPublisher.publishEvent(
                        new RetainerPeriodRolloverEvent(
                            setup.agreementId(),
                            setup.customerId(),
                            setup.periodId(),
                            UUID.randomUUID(),
                            newStart,
                            newEnd,
                            new BigDecimal("5.00"),
                            new BigDecimal("30.00"),
                            newEnd,
                            tenantSchema,
                            ORG_ID,
                            Instant.now()))));

    var summary =
        summaryRepo
            .findByCustomerIdAndRetainerId(setup.customerId(), setup.agreementId())
            .orElseThrow();
    assertThat(summary.periodStart()).isEqualTo(newStart);
    assertThat(summary.periodEnd()).isEqualTo(newEnd);
    assertThat(summary.hoursConsumed()).isEqualByComparingTo("0");
    assertThat(summary.rolloverHours()).isEqualByComparingTo("5.00");
    // Opening balance = new allotment + rollover
    assertThat(summary.hoursRemaining()).isEqualByComparingTo("35.00");
    assertThat(summary.hoursAllotted()).isEqualByComparingTo("30.00");
  }

  // ==========================================================================
  // Scenario 4 — FIRST_NAME_ROLE (default) renders "Alice (owner)"
  // ==========================================================================

  @Test
  void firstNameRoleMode_rendersMemberDisplayAsFirstNameParenRole() {
    // Ensure default mode (sync default is FIRST_NAME_ROLE — belt & braces).
    runInTenant(
        () ->
            orgSettingsService.updatePortalRetainerMemberDisplay(
                PortalRetainerMemberDisplay.FIRST_NAME_ROLE, new ActorContext(memberId, "owner")));

    var setup = createHourBankSetup("display-role", new BigDecimal("10"), LocalDate.of(2026, 8, 1));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 8, 5),
                        60,
                        true,
                        null,
                        "Drafting",
                        new ActorContext(memberId, "owner"))));

    var entries =
        entryRepo.findByRetainerIdAndEntryDateRange(
            setup.customerId(), setup.agreementId(), null, null);
    assertThat(entries).hasSize(1);
    // Member created as "Alice Ndlovu" with org role slug "owner" → display name "Owner"
    // → "Alice (Owner)".
    assertThat(entries.getFirst().memberDisplayName()).isEqualTo("Alice (Owner)");
  }

  // ==========================================================================
  // Scenario 5 — Mode change re-resolves on new events; existing entries
  //              remain unchanged (upsert is idempotent by time-entry id).
  // ==========================================================================

  @Test
  void memberDisplayModeChange_appliesToNewEntriesOnly() {
    // Start in FIRST_NAME_ROLE
    runInTenant(
        () ->
            orgSettingsService.updatePortalRetainerMemberDisplay(
                PortalRetainerMemberDisplay.FIRST_NAME_ROLE, new ActorContext(memberId, "owner")));

    var setup =
        createHourBankSetup("display-change", new BigDecimal("20"), LocalDate.of(2026, 9, 1));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 9, 2),
                        30,
                        true,
                        null,
                        "First entry (pre-mode-change)",
                        new ActorContext(memberId, "owner"))));

    // Flip to ANONYMISED — any NEW time entry should render "Team member".
    runInTenant(
        () ->
            orgSettingsService.updatePortalRetainerMemberDisplay(
                PortalRetainerMemberDisplay.ANONYMISED, new ActorContext(memberId, "owner")));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    timeEntryService.createTimeEntry(
                        setup.taskId(),
                        LocalDate.of(2026, 9, 9),
                        30,
                        true,
                        null,
                        "Second entry (post-mode-change)",
                        new ActorContext(memberId, "owner"))));

    // Fetch both — most recent first, per repo's DESC order.
    List<?> entries =
        entryRepo.findByRetainerIdAndEntryDateRange(
            setup.customerId(), setup.agreementId(), null, null);
    assertThat(entries).hasSize(2);

    var second =
        entryRepo
            .findByRetainerIdAndEntryDateRange(
                setup.customerId(), setup.agreementId(), LocalDate.of(2026, 9, 9), null)
            .getFirst();
    var first =
        entryRepo
            .findByRetainerIdAndEntryDateRange(
                setup.customerId(),
                setup.agreementId(),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 2))
            .getFirst();

    // New entry reflects the flipped mode.
    assertThat(second.memberDisplayName()).isEqualTo("Team member");
    // Pre-change entry was written with "Alice (Owner)" and is not re-resolved retroactively.
    assertThat(first.memberDisplayName()).isEqualTo("Alice (Owner)");
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  /** Test data holder for a customer + project + task + retainer setup. */
  private record TestSetup(
      UUID customerId, UUID projectId, UUID taskId, UUID agreementId, UUID periodId) {}

  private TestSetup createHourBankSetup(
      String nameSuffix, BigDecimal allocatedHours, LocalDate periodStart) {
    final UUID[] holder = new UUID[5];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      TestCustomerFactory.createActiveCustomer(
                          "Customer " + nameSuffix, nameSuffix + "@retainer-sync.test", memberId);
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
                  holder[0] = cid;
                  holder[1] = pid;
                  holder[2] = tid;
                  holder[3] = retainerResp.id();
                  holder[4] = retainerResp.currentPeriod().id();
                }));
    return new TestSetup(holder[0], holder[1], holder[2], holder[3], holder[4]);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
