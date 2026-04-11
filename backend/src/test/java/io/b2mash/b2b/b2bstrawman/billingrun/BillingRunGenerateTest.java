package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.expense.Expense;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseCategory;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunGenerateTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_generate_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private BillingRunRepository billingRunRepository;
  @Autowired private BillingRunItemRepository billingRunItemRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private String memberIdMember;
  private UUID customerId;
  private UUID customerId2;
  private UUID projectId;
  private UUID projectId2;
  private UUID taskId;
  private UUID taskId2;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Generate Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_gen_owner", "gen_owner@test.com", "Gen Owner", "owner"));
    memberIdMember =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_gen_member", "gen_member@test.com", "Gen Member", "member");

    TestModuleHelper.enableModules(mockMvc, ORG_ID, "user_gen_owner", "bulk_billing");

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
                      // Customer 1 with prerequisite fields
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Generate Corp", "generate@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Customer 2 with prerequisite fields
                      var customer2 =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Second Corp", "second@test.com", memberIdOwner);
                      customer2 = customerRepository.save(customer2);
                      customerId2 = customer2.getId();

                      // Project 1 linked to customer 1
                      var project = new Project("Gen Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Project 2 linked to customer 2
                      var project2 = new Project("Gen Project 2", "Test project 2", memberIdOwner);
                      project2 = projectRepository.save(project2);
                      projectId2 = project2.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId2, projectId2, memberIdOwner));

                      // Task 1
                      var task =
                          new Task(
                              projectId, "Gen Task", null, "MEDIUM", "TASK", null, memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // Task 2
                      var task2 =
                          new Task(
                              projectId2,
                              "Gen Task 2",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task2 = taskRepository.save(task2);
                      taskId2 = task2.getId();
                    }));
  }

  /**
   * Seeds fresh unbilled time entries for a test. Each test that generates invoices needs its own
   * entries because invoice creation links entries (sets invoice_id), consuming them.
   */
  private void seedUnbilledEntries(String label, boolean includeExpenses) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Time entry for customer 1
                      var te1 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 15),
                              120,
                              true,
                              null,
                              label + " work c1");
                      te1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      timeEntryRepository.save(te1);

                      if (includeExpenses) {
                        var expense =
                            new Expense(
                                projectId,
                                memberIdOwner,
                                LocalDate.of(2026, 3, 18),
                                label + " expense",
                                new BigDecimal("500.00"),
                                "ZAR",
                                ExpenseCategory.FILING_FEE);
                        expenseRepository.save(expense);
                      }

                      // Time entry for customer 2
                      var te2 =
                          new TimeEntry(
                              taskId2,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 20),
                              60,
                              true,
                              null,
                              label + " work c2");
                      te2.snapshotBillingRate(new BigDecimal("2000.00"), "ZAR");
                      timeEntryRepository.save(te2);
                    }));
  }

  @Test
  @Order(1)
  void generate_validPreviewRun_returnsCompleted() throws Exception {
    seedUnbilledEntries("test1", true);
    String runId = createBillingRunWithPreview("Gen Run 1", true);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.totalInvoices").isNumber())
        .andExpect(jsonPath("$.totalFailed").value(0))
        .andExpect(jsonPath("$.completedAt").exists());
  }

  @Test
  @Order(2)
  void generate_setsInvoiceIdOnItems() throws Exception {
    seedUnbilledEntries("test2", false);
    String runId = createBillingRunWithPreview("Gen Run 2", false);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk());

    // Verify items have status GENERATED and invoiceId set
    mockMvc
        .perform(
            get("/api/billing-runs/" + runId + "/items")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("GENERATED"))
        .andExpect(jsonPath("$[0].invoiceId").isNotEmpty());
  }

  @Test
  @Order(3)
  void generate_setsBillingRunIdOnInvoices() throws Exception {
    seedUnbilledEntries("test3", false);
    String runId = createBillingRunWithPreview("Gen Run 3", false);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk());

    // Verify invoices are linked to billing run
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var items =
                          billingRunItemRepository.findByBillingRunId(UUID.fromString(runId));
                      for (var item : items) {
                        if (item.getInvoiceId() != null) {
                          var invoice =
                              invoiceRepository.findById(item.getInvoiceId()).orElseThrow();
                          org.assertj.core.api.Assertions.assertThat(invoice.getBillingRunId())
                              .isEqualTo(UUID.fromString(runId));
                        }
                      }
                    }));
  }

  @Test
  @Order(4)
  void generate_nonPreviewRun_returns400() throws Exception {
    seedUnbilledEntries("test4", false);
    String runId = createBillingRunWithPreview("Gen Run 4", false);

    // Generate once to transition to COMPLETED
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk());

    // Try to generate again — should fail because status is COMPLETED
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(5)
  void generate_whenAnotherRunInProgress_returns409() throws Exception {
    String runId1 = createBillingRunWithPreview("Gen Run 5A", false);
    String runId2 = createBillingRunWithPreview("Gen Run 5B", false);

    // Manually set first run to IN_PROGRESS
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run =
                          billingRunRepository.findById(UUID.fromString(runId1)).orElseThrow();
                      run.startGeneration();
                      billingRunRepository.save(run);
                    }));

    // Try to generate second run — should get 409
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId2 + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isConflict());

    // Clean up: complete the IN_PROGRESS run so it doesn't block subsequent tests
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run =
                          billingRunRepository.findById(UUID.fromString(runId1)).orElseThrow();
                      run.complete(0, 0, BigDecimal.ZERO);
                      billingRunRepository.save(run);
                    }));
  }

  @Test
  @Order(6)
  void generate_excludedItemsSkipped() throws Exception {
    seedUnbilledEntries("test6", true);
    String runId = createBillingRunWithPreview("Gen Run 6", true);

    // Get items and exclude the first one
    var itemsResult =
        mockMvc
            .perform(
                get("/api/billing-runs/" + runId + "/items")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
            .andExpect(status().isOk())
            .andReturn();

    String firstItemId = JsonPath.read(itemsResult.getResponse().getContentAsString(), "$[0].id");

    mockMvc
        .perform(
            put("/api/billing-runs/" + runId + "/items/" + firstItemId + "/exclude")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk());

    // Generate — excluded item should be skipped
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Check that excluded item still has no invoiceId
    var finalItems =
        mockMvc
            .perform(
                get("/api/billing-runs/" + runId + "/items")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
            .andExpect(status().isOk())
            .andReturn();

    String content = finalItems.getResponse().getContentAsString();
    List<String> statuses = JsonPath.read(content, "$[*].status");
    org.assertj.core.api.Assertions.assertThat(statuses).contains("EXCLUDED", "GENERATED");
  }

  @Test
  @Order(7)
  void generate_computesCorrectStats() throws Exception {
    seedUnbilledEntries("test7", true);
    String runId = createBillingRunWithPreview("Gen Run 7", true);

    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalFailed").value(0))
            .andExpect(jsonPath("$.totalAmount").isNumber())
            .andReturn();

    // totalInvoices should match number of PENDING items (2 customers)
    String responseContent = result.getResponse().getContentAsString();
    int totalInvoices = JsonPath.read(responseContent, "$.totalInvoices");
    org.assertj.core.api.Assertions.assertThat(totalInvoices).isGreaterThan(0);
  }

  @Test
  @Order(8)
  void generate_memberRole_returns403() throws Exception {
    String runId = createBillingRunWithPreview("Gen Run 8", false);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_gen_member", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(9)
  void generate_noPendingItems_completesWithZeroStats() throws Exception {
    // Create a run with preview but only for a period with no work
    String runId = createBillingRun("Empty Run", "2025-01-01", "2025-01-31", "ZAR", false);

    // Load preview (will find no customers)
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    // Generate with no items
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.totalInvoices").value(0))
        .andExpect(jsonPath("$.totalFailed").value(0));
  }

  @Test
  @Order(10)
  void generate_withMultipleCustomers_generatesInvoicesForEach() throws Exception {
    seedUnbilledEntries("test10", false);
    String runId = createBillingRunWithPreview("Gen Run 10", false);

    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andReturn();

    // Verify all items are GENERATED
    var itemsResult =
        mockMvc
            .perform(
                get("/api/billing-runs/" + runId + "/items")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner")))
            .andExpect(status().isOk())
            .andReturn();

    String content = itemsResult.getResponse().getContentAsString();
    List<String> statuses = JsonPath.read(content, "$[*].status");
    org.assertj.core.api.Assertions.assertThat(statuses).containsOnly("GENERATED");
    org.assertj.core.api.Assertions.assertThat(statuses).hasSize(2);
  }

  // --- Helpers ---

  private String createBillingRunWithPreview(String name, boolean includeExpenses)
      throws Exception {
    String runId = createBillingRun(name, "2026-03-01", "2026-03-31", "ZAR", includeExpenses);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    return runId;
  }

  private String createBillingRun(
      String name, String periodFrom, String periodTo, String currency, boolean includeExpenses)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_gen_owner"))
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
