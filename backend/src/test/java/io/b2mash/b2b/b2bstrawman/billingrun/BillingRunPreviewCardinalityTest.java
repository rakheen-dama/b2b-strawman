package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.expense.Expense;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseCategory;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestModuleHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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

/**
 * Regression tests for OBS-2104b — discoverCustomers() Cartesian-product row multiplication.
 *
 * <p>Pre-fix the wizard step-2 SUMs (time / expense / disbursement) were inflated by sibling LEFT
 * JOIN cardinalities. e.g. a customer with 9 tasks + 1 disbursement reported disbursement_total = 9
 * × actual instead of 1 × actual. The CTE rewrite aggregates each source table per-customer in
 * isolation before joining, so the SUMs are now exact.
 *
 * <p>Each test seeds a different cardinality combination and verifies the wizard preview totals
 * match a single-source calculation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingRunPreviewCardinalityTest {

  private static final String ORG_ID = "org_billing_cardinality_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Cardinality Test Org", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_card_owner", "card_owner@test.com", "Card Owner", "owner"));
    TestModuleHelper.enableModules(mockMvc, ORG_ID, "user_card_owner", "bulk_billing");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  /**
   * Test 1 — the OBS-2104b headline scenario. Customer with 9 tasks (no time entries) + 1
   * disbursement at R 1,250 (no VAT). Expect unbilledExpenseAmount = 1250 (was 11250 pre-fix).
   */
  @Test
  void discoverCustomers_manyTasksOneDisbursement_doesNotInflateDisbursementTotal()
      throws Exception {
    UUID customerId =
        seedCustomerWithProject(
            "Sipho Reproduction",
            "sipho-repro@test.com",
            project -> {
              for (int i = 0; i < 9; i++) {
                taskRepository.save(
                    new Task(
                        project, "RAF Task " + i, null, "MEDIUM", "TASK", null, memberIdOwner));
              }
            });
    seedDisbursement(customerId, new BigDecimal("1250.00"), BigDecimal.ZERO);

    String runId = createBillingRun("Card Test 1", "2026-03-01", "2026-03-31", "ZAR", true);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_card_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerIds": ["%s"]}
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCustomers").value(1))
        .andExpect(jsonPath("$.items[0].unbilledExpenseCount").value(1))
        // Pre-fix this returned 11250.00 (1250 × 9 tasks). Must be 1250.00 exactly.
        .andExpect(jsonPath("$.items[0].unbilledExpenseAmount").value(1250.00))
        .andExpect(jsonPath("$.items[0].unbilledTimeCount").value(0))
        .andExpect(jsonPath("$.items[0].unbilledTimeAmount").value(0));
  }

  /**
   * Test 2 — multiple expenses + multiple tasks. 5 tasks, 2 expenses (R 100 + R 200, no markup).
   * Expect unbilledExpenseAmount = 300 (pre-fix would have been 1500 = 300 × 5 tasks).
   */
  @Test
  void discoverCustomers_manyTasksMultipleExpenses_doesNotInflateExpenseTotal() throws Exception {
    UUID customerId =
        seedCustomerWithProject(
            "Many Tasks Many Expenses",
            "manymany@test.com",
            project -> {
              for (int i = 0; i < 5; i++) {
                taskRepository.save(
                    new Task(project, "Task " + i, null, "MEDIUM", "TASK", null, memberIdOwner));
              }
              expenseRepository.save(
                  new Expense(
                      project,
                      memberIdOwner,
                      LocalDate.of(2026, 3, 5),
                      "Filing fee 1",
                      new BigDecimal("100.00"),
                      "ZAR",
                      ExpenseCategory.FILING_FEE));
              expenseRepository.save(
                  new Expense(
                      project,
                      memberIdOwner,
                      LocalDate.of(2026, 3, 12),
                      "Filing fee 2",
                      new BigDecimal("200.00"),
                      "ZAR",
                      ExpenseCategory.FILING_FEE));
            });

    String runId = createBillingRun("Card Test 2", "2026-03-01", "2026-03-31", "ZAR", true);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_card_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerIds": ["%s"]}
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].unbilledExpenseCount").value(2))
        .andExpect(jsonPath("$.items[0].unbilledExpenseAmount").value(300.00));
  }

  /**
   * Test 3 — the cross-source scenario. 3 tasks + 4 time entries + 1 disbursement. Time and
   * disbursement totals must each be exact, not multiplied by the other axis.
   */
  @Test
  void discoverCustomers_timeAndDisbursement_neitherInflatesTheOther() throws Exception {
    UUID customerId =
        seedCustomerWithProject(
            "Cross Source",
            "cross@test.com",
            project -> {
              UUID taskId = null;
              for (int i = 0; i < 3; i++) {
                var task =
                    taskRepository.save(
                        new Task(
                            project, "Task " + i, null, "MEDIUM", "TASK", null, memberIdOwner));
                if (i == 0) {
                  taskId = task.getId();
                }
              }
              // 4 time entries on the first task — 1h each at R 1,500.
              for (int i = 0; i < 4; i++) {
                var te =
                    new TimeEntry(
                        taskId,
                        memberIdOwner,
                        LocalDate.of(2026, 3, 10 + i),
                        60,
                        true,
                        null,
                        "Time " + i);
                te.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                timeEntryRepository.save(te);
              }
            });
    seedDisbursement(customerId, new BigDecimal("500.00"), BigDecimal.ZERO);

    String runId = createBillingRun("Card Test 3", "2026-03-01", "2026-03-31", "ZAR", true);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_card_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerIds": ["%s"]}
                    """
                        .formatted(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].unbilledTimeCount").value(4))
        // 4 hours × 1500 = 6000. Pre-fix this would have been 6000 × 1 disbursement = 6000
        // (lucky no inflation here since disbursements_count = 1). The trap below tests
        // the disbursement side which inflates by tasks_count.
        .andExpect(jsonPath("$.items[0].unbilledTimeAmount").value(6000.00))
        .andExpect(jsonPath("$.items[0].unbilledExpenseCount").value(1))
        // Pre-fix this returned 1500 (500 × 3 tasks). Must be 500 exactly.
        .andExpect(jsonPath("$.items[0].unbilledExpenseAmount").value(500.00));
  }

  // --- Helpers ---

  private UUID seedCustomerWithProject(
      String customerName, String customerEmail, java.util.function.Consumer<UUID> projectSeedFn) {
    final UUID[] customerIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                          customerName, customerEmail, memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerIdHolder[0] = customer.getId();

                  var project = new Project(customerName + " Matter", "Test matter", memberIdOwner);
                  project.setCustomerId(customer.getId());
                  project = projectRepository.save(project);

                  customerProjectRepository.save(
                      new CustomerProject(customer.getId(), project.getId(), memberIdOwner));

                  projectSeedFn.accept(project.getId());
                }));
    return customerIdHolder[0];
  }

  private void seedDisbursement(UUID customerId, BigDecimal amount, BigDecimal vatAmount) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Look up the customer's project via customer_projects (single project per
                  // test fixture).
                  var cps = customerProjectRepository.findByCustomerId(customerId);
                  UUID projectId = cps.get(0).getProjectId();

                  var d =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.SHERIFF_FEES.name(),
                          "Sheriff service",
                          amount,
                          VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
                          vatAmount,
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 3, 15),
                          "Sheriff Joburg North",
                          "REF-001",
                          null,
                          memberIdOwner);
                  d.submitForApproval();
                  d.approve(memberIdOwner, "ok");
                  disbursementRepository.saveAndFlush(d);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String createBillingRun(
      String name, String periodFrom, String periodTo, String currency, boolean includeExpenses)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_card_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "periodFrom": "%s",
                          "periodTo": "%s",
                          "currency": "%s",
                          "includeExpenses": %s,
                          "includeRetainers": false
                        }
                        """
                            .formatted(name, periodFrom, periodTo, currency, includeExpenses)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
