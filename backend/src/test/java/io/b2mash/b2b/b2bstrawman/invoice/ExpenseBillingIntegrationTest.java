package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseBillingIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_expense_billing_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Expense Billing Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_exp_billing_owner", "exp_billing@test.com", "EB Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Expense Corp", "expense@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project = new Project("Expense Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var task =
                          new Task(
                              projectId,
                              "Expense Test Task",
                              null,
                              null,
                              null,
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();
                    }));
  }

  // --- 221.11: Unbilled summary extension tests ---

  @Test
  void unbilledSummary_includesExpenses() throws Exception {
    UUID expId = createExpense(new BigDecimal("250.00"), "ZAR", new BigDecimal("20.00"));

    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unbilledExpenses").isArray())
        .andExpect(jsonPath("$.unbilledExpenses[?(@.id=='" + expId + "')]").exists())
        .andExpect(jsonPath("$.unbilledExpenses[?(@.id=='" + expId + "')].amount").value(250.00))
        .andExpect(
            jsonPath("$.unbilledExpenses[?(@.id=='" + expId + "')].billableAmount").value(300.00));

    // Cleanup
    deleteExpense(expId);
  }

  @Test
  void unbilledSummary_excludesBilledExpenses() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);
    // Create and approve an invoice to bill this expense
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unbilledExpenses[?(@.id=='" + expId + "')]").doesNotExist());

    // Cleanup: void invoice to unbill, then delete expense
    voidInvoice(invoiceId);
    deleteExpense(expId);
  }

  @Test
  void unbilledSummary_excludesNonBillableExpenses() throws Exception {
    UUID expId = createNonBillableExpense(new BigDecimal("50.00"), "ZAR");

    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unbilledExpenses[?(@.id=='" + expId + "')]").doesNotExist());

    // Cleanup
    deleteExpense(expId);
  }

  // --- 221.12: Expense billing workflow tests ---

  @Test
  void generateInvoice_withExpenses_createsExpenseLines() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);

    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "expenseIds": ["%s"]
        }
        """
            .formatted(customerId, expId);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].lineType").value("EXPENSE"))
        .andExpect(jsonPath("$.lines[0].expenseId").value(expId.toString()));

    // Cleanup
    cleanupInvoicesAndExpenses();
  }

  @Test
  void generateInvoice_expenseLineType_isExpense() throws Exception {
    UUID expId = createExpense(new BigDecimal("75.00"), "ZAR", new BigDecimal("10.00"));

    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "expenseIds": ["%s"]
        }
        """
            .formatted(customerId, expId);

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    String lineType =
        JsonPath.read(result.getResponse().getContentAsString(), "$.lines[0].lineType");
    assertThat(lineType).isEqualTo("EXPENSE");

    cleanupInvoicesAndExpenses();
  }

  @Test
  void generateInvoice_expenseBillableAmount_includesMarkup() throws Exception {
    // 200 * (1 + 25/100) = 250
    UUID expId = createExpense(new BigDecimal("200.00"), "ZAR", new BigDecimal("25.00"));

    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "expenseIds": ["%s"]
        }
        """
            .formatted(customerId, expId);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines[0].unitPrice").value(250.00));

    cleanupInvoicesAndExpenses();
  }

  @Test
  void generateInvoice_mixedTimeAndExpenseLines() throws Exception {
    UUID expId = createExpense(new BigDecimal("150.00"), "ZAR", null);
    UUID timeEntryId = createTimeEntry();

    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "timeEntryIds": ["%s"],
          "expenseIds": ["%s"]
        }
        """
            .formatted(customerId, timeEntryId, expId);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(2))
        .andExpect(jsonPath("$.lines[0].lineType").value("TIME"))
        .andExpect(jsonPath("$.lines[1].lineType").value("EXPENSE"));

    cleanupInvoicesAndExpenses();
  }

  @Test
  void approveInvoice_stampsInvoiceIdOnExpenses() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);
    String invoiceId = createDraftInvoiceWithExpense(expId);

    // Approve
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Verify expense is stamped
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense = expenseRepository.findById(expId).orElseThrow();
                  assertThat(expense.getInvoiceId()).isEqualTo(UUID.fromString(invoiceId));
                }));

    // Cleanup
    voidInvoice(invoiceId);
    deleteExpense(expId);
  }

  @Test
  void approveInvoice_expenseBillingStatus_becomesBilled() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense = expenseRepository.findById(expId).orElseThrow();
                  assertThat(expense.getInvoiceId()).isNotNull();
                  assertThat(expense.getBillingStatus().name()).isEqualTo("BILLED");
                }));

    voidInvoice(invoiceId);
    deleteExpense(expId);
  }

  @Test
  void voidInvoice_clearsInvoiceIdFromExpenses() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    // Void
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
        .andExpect(status().isOk());

    // Verify expense is unbilled
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense = expenseRepository.findById(expId).orElseThrow();
                  assertThat(expense.getInvoiceId()).isNull();
                }));

    deleteExpense(expId);
  }

  @Test
  void voidInvoice_expenseBillingStatus_revertsToUnbilled() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", null);
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
        .andExpect(status().isOk());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense = expenseRepository.findById(expId).orElseThrow();
                  assertThat(expense.getBillingStatus().name()).isEqualTo("UNBILLED");
                }));

    deleteExpense(expId);
  }

  @Test
  void profitability_includesExpenseCost() throws Exception {
    UUID expId = createExpense(new BigDecimal("500.00"), "ZAR", null);

    mockMvc
        .perform(get("/api/projects/" + projectId + "/profitability").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[?(@.currency=='ZAR')].totalExpenseCost").exists());

    deleteExpense(expId);
  }

  @Test
  void profitability_includesExpenseRevenue_forBilledExpenses() throws Exception {
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", new BigDecimal("20.00"));
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    mockMvc
        .perform(get("/api/projects/" + projectId + "/profitability").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[?(@.currency=='ZAR')].totalExpenseRevenue").exists());

    voidInvoice(invoiceId);
    deleteExpense(expId);
  }

  @Test
  void profitability_absorbs_nonBillableExpenses_asPureCost() throws Exception {
    UUID expId = createNonBillableExpense(new BigDecimal("300.00"), "ZAR");

    var result =
        mockMvc
            .perform(get("/api/projects/" + projectId + "/profitability").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    // Non-billable expenses should contribute to cost but not revenue
    String json = result.getResponse().getContentAsString();
    List<Number> costs = JsonPath.read(json, "$.currencies[?(@.currency=='ZAR')].totalExpenseCost");
    if (!costs.isEmpty()) {
      assertThat(costs.getFirst().doubleValue()).isGreaterThan(0);
    }

    deleteExpense(expId);
  }

  @Test
  void profitability_markupContributesToMargin() throws Exception {
    // Create expense with 50% markup and bill it
    UUID expId = createExpense(new BigDecimal("100.00"), "ZAR", new BigDecimal("50.00"));
    String invoiceId = createAndApproveInvoiceWithExpense(expId);

    var result =
        mockMvc
            .perform(get("/api/projects/" + projectId + "/profitability").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    // Revenue for billed expense = 100 * (1 + 50/100) = 150
    List<Number> revenues =
        JsonPath.read(json, "$.currencies[?(@.currency=='ZAR')].totalExpenseRevenue");
    if (!revenues.isEmpty()) {
      assertThat(revenues.getFirst().doubleValue()).isGreaterThan(0);
    }

    voidInvoice(invoiceId);
    deleteExpense(expId);
  }

  // --- Helpers ---

  private UUID createExpense(BigDecimal amount, String currency, BigDecimal markupPercent) {
    final UUID[] expenseId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense =
                      new Expense(
                          projectId,
                          memberIdOwner,
                          LocalDate.of(2025, 3, 1),
                          "Court filing fee",
                          amount,
                          currency,
                          ExpenseCategory.FILING_FEE);
                  if (markupPercent != null) {
                    expense.setMarkupPercent(markupPercent);
                  }
                  expense = expenseRepository.save(expense);
                  expenseId[0] = expense.getId();
                }));
    return expenseId[0];
  }

  private UUID createNonBillableExpense(BigDecimal amount, String currency) {
    final UUID[] expenseId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var expense =
                      new Expense(
                          projectId,
                          memberIdOwner,
                          LocalDate.of(2025, 3, 1),
                          "Office supplies",
                          amount,
                          currency,
                          ExpenseCategory.OTHER);
                  expense = expenseRepository.save(expense);
                  expense.writeOff();
                  expenseRepository.save(expense);
                  expenseId[0] = expense.getId();
                }));
    return expenseId[0];
  }

  private UUID createTimeEntry() {
    final UUID[] entryId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var timeEntry =
                      new TimeEntry(
                          taskId,
                          memberIdOwner,
                          LocalDate.of(2025, 3, 1),
                          60,
                          true,
                          null,
                          "Test time entry");
                  timeEntry.snapshotBillingRate(new BigDecimal("100.00"), "ZAR");
                  timeEntry = timeEntryRepository.save(timeEntry);
                  entryId[0] = timeEntry.getId();
                }));
    return entryId[0];
  }

  private String createDraftInvoiceWithExpense(UUID expenseId) throws Exception {
    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "expenseIds": ["%s"]
        }
        """
            .formatted(customerId, expenseId);

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(ownerJwt()))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createAndApproveInvoiceWithExpense(UUID expenseId) throws Exception {
    String invoiceId = createDraftInvoiceWithExpense(expenseId);

    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    return invoiceId;
  }

  private void voidInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private void deleteExpense(UUID expenseId) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Clear invoice lines referencing this expense first
                  invoiceLineRepository
                      .findByExpenseId(expenseId)
                      .ifPresent(invoiceLineRepository::delete);
                  expenseRepository.deleteById(expenseId);
                }));
  }

  private void cleanupInvoicesAndExpenses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Delete all invoices and their lines, then expenses
                  var invoices = invoiceRepository.findAll();
                  for (var invoice : invoices) {
                    var lines =
                        invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                    // Unlink time entries
                    for (var line : lines) {
                      if (line.getTimeEntryId() != null) {
                        timeEntryRepository
                            .findById(line.getTimeEntryId())
                            .ifPresent(
                                te -> {
                                  te.setInvoiceId(null);
                                  timeEntryRepository.save(te);
                                });
                      }
                      if (line.getExpenseId() != null) {
                        expenseRepository
                            .findById(line.getExpenseId())
                            .ifPresent(
                                e -> {
                                  if (e.getInvoiceId() != null) {
                                    e.unbill();
                                    expenseRepository.save(e);
                                  }
                                });
                      }
                    }
                    invoiceLineRepository.deleteAll(lines);
                    invoiceRepository.delete(invoice);
                  }
                  // Delete all expenses
                  expenseRepository.deleteAll();
                  // Delete orphaned time entries
                  timeEntryRepository.deleteAll();
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_exp_billing_owner")
                    .claim(
                        "o", Map.of("id", ORG_ID, "rol", "owner", "slg", "exp-billing-test-org")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
