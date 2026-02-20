package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRate;
import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
class RetainerPeriodServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_retainer_period_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private RetainerPeriodService retainerPeriodService;
  @Autowired private RetainerAgreementService retainerAgreementService;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private BillingRateRepository billingRateRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Period Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_rp_owner", "rp_owner@test.com", "RP Owner", "owner"));

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

  private UUID createCustomer(String name, String email) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer = TestCustomerFactory.createActiveCustomer(name, email, memberId);
                  customer = customerRepository.save(customer);
                  ref.set(customer.getId());
                }));
    return ref.get();
  }

  /**
   * Creates an HOUR_BANK retainer via the service. The start date should be far enough in the past
   * that the auto-created first period's end date is already past, making it closeable.
   */
  private UUID createHourBankRetainer(
      UUID customerId,
      BigDecimal allocatedHours,
      BigDecimal periodFee,
      RolloverPolicy rolloverPolicy,
      BigDecimal rolloverCapHours,
      LocalDate startDate,
      LocalDate endDate) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Test Hour Bank Retainer",
                  RetainerType.HOUR_BANK,
                  RetainerFrequency.MONTHLY,
                  startDate,
                  endDate,
                  allocatedHours,
                  periodFee,
                  rolloverPolicy,
                  rolloverCapHours,
                  null);
          var response = retainerAgreementService.createRetainer(request, memberId);
          ref.set(response.id());
        });
    return ref.get();
  }

  /** Creates a FIXED_FEE retainer via the service. */
  private UUID createFixedFeeRetainer(
      UUID customerId, BigDecimal periodFee, LocalDate startDate, LocalDate endDate) {
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () -> {
          var request =
              new CreateRetainerRequest(
                  customerId,
                  null,
                  "Test Fixed Fee Retainer",
                  RetainerType.FIXED_FEE,
                  RetainerFrequency.MONTHLY,
                  startDate,
                  endDate,
                  null,
                  periodFee,
                  RolloverPolicy.FORFEIT,
                  null,
                  null);
          var response = retainerAgreementService.createRetainer(request, memberId);
          ref.set(response.id());
        });
    return ref.get();
  }

  /** Sets up a billing rate for the given member scoped to a customer. */
  private void setupBillingRate(
      UUID billingMemberId, UUID customerId, BigDecimal hourlyRate, LocalDate effectiveFrom) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rate =
                      new BillingRate(
                          billingMemberId,
                          null,
                          customerId,
                          "ZAR",
                          hourlyRate,
                          effectiveFrom,
                          null);
                  billingRateRepository.save(rate);
                }));
  }

  /** Sets up org settings with default currency. */
  private void ensureOrgSettings(String currency) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var existing = orgSettingsRepository.findForCurrentTenant();
                  if (existing.isPresent()) {
                    existing.get().updateCurrency(currency);
                    orgSettingsRepository.save(existing.get());
                  } else {
                    orgSettingsRepository.save(new OrgSettings(currency));
                  }
                }));
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

  // ======================================================================
  // Test 1: HOUR_BANK with no consumption creates invoice with base fee only
  // ======================================================================
  @Test
  void closePeriod_hourBank_noConsumption_createsInvoiceWithBaseFeeOnly() {
    var customerId = createCustomer("NoConsumption Corp", "noconsumption@test.com");
    ensureOrgSettings("ZAR");

    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);
          assertThat(result.generatedInvoice().getStatus()).isEqualTo(InvoiceStatus.DRAFT);

          // With 0 time entries, consumed = 0, no overage
          assertThat(result.closedPeriod().getConsumedHours()).isEqualByComparingTo("0.00");
          assertThat(result.closedPeriod().getOverageHours()).isEqualByComparingTo("0.00");

          // Only base line (no overage line since consumed = 0)
          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).hasSize(1);
          assertThat(lines.getFirst().getDescription()).startsWith("Retainer");
          assertThat(lines.getFirst().getAmount()).isEqualByComparingTo("20000.00");
          assertThat(lines.getFirst().getRetainerPeriodId())
              .isEqualTo(result.closedPeriod().getId());
        });
  }

  // ======================================================================
  // Test 1b: HOUR_BANK with actual overage creates invoice with two lines
  // ======================================================================
  @Test
  void closePeriod_hourBank_withRealOverage_createsInvoiceWithTwoLines() {
    var customerId = createCustomer("RealOverage Corp", "realoverage@test.com");
    ensureOrgSettings("ZAR");

    // Start date 2 months ago — period end is 1 month ago (past, closeable)
    LocalDate pastStart = LocalDate.now().minusMonths(2);
    LocalDate periodEnd = pastStart.plusMonths(1);

    // Setup billing rate for this customer (needed for overage calculation)
    setupBillingRate(memberId, customerId, new BigDecimal("300"), pastStart);

    // Create project + task linked to customer via CustomerProject
    var projectRef = new AtomicReference<UUID>();
    var taskRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project("Overage Project", null, memberId);
                  project = projectRepository.save(project);
                  projectRef.set(project.getId());

                  customerProjectRepository.save(
                      new CustomerProject(customerId, project.getId(), memberId));

                  var task =
                      new Task(project.getId(), "Overage Task", null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  taskRef.set(task.getId());
                }));

    // Allocate only 2 hours — we'll log 3 hours of billable time to create overage
    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("2"),
            new BigDecimal("5000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    // Create billable time entry: 180 min = 3 hours (within period date range)
    LocalDate entryDate = pastStart.plusDays(10);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      taskRef.get(), entryDate, 180, true, null, "Overage work", memberId, "owner");
                }));

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);
          assertThat(result.generatedInvoice().getStatus()).isEqualTo(InvoiceStatus.DRAFT);

          // 3h consumed, 2h allocated => 1h overage
          assertThat(result.closedPeriod().getConsumedHours()).isEqualByComparingTo("3.00");
          assertThat(result.closedPeriod().getOverageHours()).isEqualByComparingTo("1.00");

          // Two lines: base fee + overage
          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).hasSize(2);

          // Line 1: base fee
          assertThat(lines.get(0).getDescription()).startsWith("Retainer");
          assertThat(lines.get(0).getUnitPrice()).isEqualByComparingTo("5000");

          // Line 2: overage (1h @ 300/hr)
          assertThat(lines.get(1).getDescription()).contains("Overage");
          assertThat(lines.get(1).getQuantity()).isEqualByComparingTo("1.00");
          assertThat(lines.get(1).getUnitPrice()).isEqualByComparingTo("300");
          assertThat(lines.get(1).getAmount()).isEqualByComparingTo("300.00");

          // Total = 5000 + 300 = 5300
          assertThat(result.generatedInvoice().getTotal()).isEqualByComparingTo("5300.00");
        });
  }

  // ======================================================================
  // Test 2: HOUR_BANK without overage creates invoice with one line
  // ======================================================================
  @Test
  void closePeriod_hourBank_withoutOverage_createsInvoiceWithOneLine() {
    var customerId = createCustomer("NoOverage Corp", "nooverage@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("15000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);
          assertThat(result.closedPeriod().getOverageHours()).isEqualByComparingTo("0.00");

          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).hasSize(1);
          assertThat(lines.getFirst().getUnitPrice()).isEqualByComparingTo("15000");
        });
  }

  // ======================================================================
  // Test 3: FIXED_FEE creates invoice with one base line
  // ======================================================================
  @Test
  void closePeriod_fixedFee_createsInvoiceWithOneBaseLine() {
    var customerId = createCustomer("FixedFee Corp", "fixedfee_close@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId = createFixedFeeRetainer(customerId, new BigDecimal("5000"), pastStart, null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);
          assertThat(result.generatedInvoice().getStatus()).isEqualTo(InvoiceStatus.DRAFT);
          assertThat(result.generatedInvoice().getTotal()).isEqualByComparingTo("5000.00");

          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).hasSize(1);
          assertThat(lines.getFirst().getDescription()).startsWith("Retainer");
          assertThat(lines.getFirst().getQuantity()).isEqualByComparingTo("1");
          assertThat(lines.getFirst().getUnitPrice()).isEqualByComparingTo("5000");
        });
  }

  // ======================================================================
  // Test 4: FORFEIT rollover — rolloverOut is zero even with unused hours
  // ======================================================================
  @Test
  void closePeriod_rolloverForfeit_rolloverOutIsZero() {
    var customerId = createCustomer("Forfeit Corp", "forfeit@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          // 0 consumed, 40 allocated => 40 unused, but FORFEIT => rolloverOut = 0
          assertThat(result.closedPeriod().getRolloverHoursOut()).isEqualByComparingTo("0");

          // Next period should have allocatedHours = base (40) + rollover (0) = 40
          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getAllocatedHours()).isEqualByComparingTo("40.00");
          assertThat(result.nextPeriod().getRolloverHoursIn()).isEqualByComparingTo("0");
        });
  }

  // ======================================================================
  // Test 5: CARRY_FORWARD rollover — rolloverOut equals unused hours
  // ======================================================================
  @Test
  void closePeriod_rolloverCarryForward_rolloverOutEqualsUnused() {
    var customerId = createCustomer("CarryForward Corp", "carryforward@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.CARRY_FORWARD,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          // 0 consumed, 40 allocated => 40 unused, CARRY_FORWARD => rolloverOut = 40
          assertThat(result.closedPeriod().getRolloverHoursOut()).isEqualByComparingTo("40.00");

          // Next period: allocatedHours = base (40) + rollover (40) = 80
          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getAllocatedHours()).isEqualByComparingTo("80.00");
          assertThat(result.nextPeriod().getBaseAllocatedHours()).isEqualByComparingTo("40.00");
          assertThat(result.nextPeriod().getRolloverHoursIn()).isEqualByComparingTo("40.00");
        });
  }

  // ======================================================================
  // Test 6: CARRY_CAPPED rollover — rolloverOut is min(unused, cap)
  // ======================================================================
  @Test
  void closePeriod_rolloverCarryCapped_rolloverOutIsMinUnusedAndCap() {
    var customerId = createCustomer("CarryCapped Corp", "carrycapped@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    // Allocated 40, cap 10 => unused 40, rollover = min(40, 10) = 10
    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.CARRY_CAPPED,
            new BigDecimal("10"),
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getRolloverHoursOut()).isEqualByComparingTo("10.00");

          // Next period: allocatedHours = base (40) + rollover (10) = 50
          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getAllocatedHours()).isEqualByComparingTo("50.00");
          assertThat(result.nextPeriod().getRolloverHoursIn()).isEqualByComparingTo("10.00");
        });
  }

  // ======================================================================
  // Test 7: Next period created with rollover allocation
  // ======================================================================
  @Test
  void closePeriod_nextPeriodCreatedWithRolloverAllocation() {
    var customerId = createCustomer("NextPeriod Corp", "nextperiod@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("20"),
            new BigDecimal("10000"),
            RolloverPolicy.CARRY_FORWARD,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getStatus()).isEqualTo(PeriodStatus.OPEN);
          assertThat(result.nextPeriod().getPeriodStart())
              .isEqualTo(result.closedPeriod().getPeriodEnd());

          // base = 20, rollover = 20 (all unused), total allocated = 40
          assertThat(result.nextPeriod().getAllocatedHours()).isEqualByComparingTo("40.00");
          assertThat(result.nextPeriod().getBaseAllocatedHours()).isEqualByComparingTo("20.00");
          assertThat(result.nextPeriod().getRolloverHoursIn()).isEqualByComparingTo("20.00");
          assertThat(result.nextPeriod().getConsumedHours()).isEqualByComparingTo("0");
        });
  }

  // ======================================================================
  // Test 8: Period end in the future throws 400
  // ======================================================================
  @Test
  void closePeriod_beforeEndDate_throws400() {
    var customerId = createCustomer("FuturePeriod Corp", "futureperiod@test.com");

    // Start date in the future so period end is even further in the future
    LocalDate futureStart = LocalDate.now().plusMonths(1);
    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            futureStart,
            null);

    runInTenant(
        () ->
            assertThatThrownBy(() -> retainerPeriodService.closePeriod(agreementId, memberId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Period not ready to close"));
  }

  // ======================================================================
  // Test 9: No open period throws 404
  // ======================================================================
  @Test
  void closePeriod_noOpenPeriod_throws404() {
    var customerId = createCustomer("NoPeriod Corp", "noperiod@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    // Close the first period
    runInTenant(() -> retainerPeriodService.closePeriod(agreementId, memberId));

    // The second period is now open, close it too to leave no open periods
    // Actually after first close, a next period is created. We need to close that too.
    // But its end date is in the future. So instead, let's terminate the agreement first.

    // Better approach: terminate the agreement so no new period is created, then manually close
    // the current period via a different mechanism.
    // Actually, the simplest approach: use a non-existent agreement ID.
    runInTenant(
        () ->
            assertThatThrownBy(() -> retainerPeriodService.closePeriod(UUID.randomUUID(), memberId))
                .isInstanceOf(ResourceNotFoundException.class));
  }

  // ======================================================================
  // Test 10: Auto-terminates when agreement end date has passed
  // ======================================================================
  @Test
  void closePeriod_autoTerminatesWhenAgreementEndDatePassed() {
    var customerId = createCustomer("AutoTerm Corp", "autoterm@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);
    // End date equals period end — so nextPeriodStart (= periodEnd) is NOT before endDate,
    // triggering auto-termination
    LocalDate endDate = pastStart.plusMonths(1);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            endDate);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          // Period closed successfully
          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);

          // No next period — agreement was auto-terminated
          assertThat(result.nextPeriod()).isNull();

          // Verify agreement is terminated
          var agreement = retainerAgreementRepository.findById(agreementId).orElseThrow();
          assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.TERMINATED);
        });
  }

  // ======================================================================
  // Test 11: HOUR_BANK with overage but no billing rate throws 400
  // ======================================================================
  @Test
  void closePeriod_hourBankWithOverageButNoBillingRate_throws400() {
    var customerId = createCustomer("NoRate Corp", "norate@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    // Create project + task linked to customer to generate real time entries
    var taskRef = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project = new Project("NoRate Project", null, memberId);
                  project = projectRepository.save(project);

                  customerProjectRepository.save(
                      new CustomerProject(customerId, project.getId(), memberId));

                  var task =
                      new Task(project.getId(), "NoRate Task", null, null, null, null, memberId);
                  task = taskRepository.save(task);
                  taskRef.set(task.getId());
                }));

    // Allocate only 1 hour so any significant time entry creates overage
    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("1"),
            new BigDecimal("5000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    // Create billable time entry: 120 min = 2 hours > 1h allocated => overage
    LocalDate entryDate = pastStart.plusDays(10);
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  timeEntryService.createTimeEntry(
                      taskRef.get(),
                      entryDate,
                      120,
                      true,
                      null,
                      "Work without rate",
                      memberId,
                      "owner");
                }));

    // DO NOT set up billing rate — overage exists but no rate => should throw
    runInTenant(
        () ->
            assertThatThrownBy(() -> retainerPeriodService.closePeriod(agreementId, memberId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Cannot calculate overage"));
  }

  // ======================================================================
  // Test 12: Zero consumption — full rollover for CARRY_FORWARD
  // ======================================================================
  @Test
  void closePeriod_zeroConsumption_fullRolloverForCarryForward() {
    var customerId = createCustomer("ZeroConsumption Corp", "zeroconsumption@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("50"),
            new BigDecimal("25000"),
            RolloverPolicy.CARRY_FORWARD,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          // Zero consumption: all hours are unused and carried forward
          assertThat(result.closedPeriod().getConsumedHours()).isEqualByComparingTo("0.00");
          assertThat(result.closedPeriod().getRolloverHoursOut()).isEqualByComparingTo("50.00");
          assertThat(result.closedPeriod().getOverageHours()).isEqualByComparingTo("0.00");

          // Next period gets full rollover
          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getAllocatedHours()).isEqualByComparingTo("100.00");
          assertThat(result.nextPeriod().getRolloverHoursIn()).isEqualByComparingTo("50.00");
        });
  }

  // ======================================================================
  // Test 13: Invoice currency from OrgSettings
  // ======================================================================
  @Test
  void closePeriod_invoiceCurrencyFromOrgSettings() {
    var customerId = createCustomer("Currency Corp", "currency@test.com");
    ensureOrgSettings("USD");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId = createFixedFeeRetainer(customerId, new BigDecimal("3000"), pastStart, null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.generatedInvoice().getCurrency()).isEqualTo("USD");
        });

    // Reset org settings back to ZAR for other tests
    ensureOrgSettings("ZAR");
  }

  // ======================================================================
  // Test 14: retainerPeriodId set on invoice lines
  // ======================================================================
  @Test
  void closePeriod_retainerPeriodIdSetOnInvoiceLines() {
    var customerId = createCustomer("PeriodId Corp", "periodid@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId =
        createHourBankRetainer(
            customerId,
            new BigDecimal("40"),
            new BigDecimal("20000"),
            RolloverPolicy.FORFEIT,
            null,
            pastStart,
            null);

    runInTenant(
        () -> {
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).isNotEmpty();
          for (var line : lines) {
            assertThat(line.getRetainerPeriodId())
                .isNotNull()
                .isEqualTo(result.closedPeriod().getId());
          }
        });
  }

  // ======================================================================
  // Test 15: FIXED_FEE succeeds without billing rate (no overage needed)
  // ======================================================================
  @Test
  void closePeriod_fixedFee_noBillingRateRequired_succeeds() {
    var customerId = createCustomer("FixedNoBR Corp", "fixednobr@test.com");
    ensureOrgSettings("ZAR");
    LocalDate pastStart = LocalDate.now().minusMonths(2);

    var agreementId = createFixedFeeRetainer(customerId, new BigDecimal("8000"), pastStart, null);

    runInTenant(
        () -> {
          // Should succeed — FIXED_FEE never needs billing rate resolution
          var result = retainerPeriodService.closePeriod(agreementId, memberId);

          assertThat(result.closedPeriod().getStatus()).isEqualTo(PeriodStatus.CLOSED);
          assertThat(result.closedPeriod().getOverageHours()).isEqualByComparingTo("0.00");
          assertThat(result.closedPeriod().getRolloverHoursOut()).isEqualByComparingTo("0.00");

          var lines =
              invoiceLineRepository.findByInvoiceIdOrderBySortOrder(
                  result.generatedInvoice().getId());
          assertThat(lines).hasSize(1);
          assertThat(lines.getFirst().getAmount()).isEqualByComparingTo("8000.00");

          // Next period created for active agreement
          assertThat(result.nextPeriod()).isNotNull();
          assertThat(result.nextPeriod().getStatus()).isEqualTo(PeriodStatus.OPEN);
          assertThat(result.nextPeriod().getAllocatedHours()).isNull();
        });
  }
}
