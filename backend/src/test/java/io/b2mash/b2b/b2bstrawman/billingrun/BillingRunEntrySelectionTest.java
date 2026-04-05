package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
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
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunEntrySelectionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_selection_test";

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
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private String billingRunId;
  private String itemId;
  private UUID timeEntryId1;
  private UUID timeEntryId2;
  private UUID expenseId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Selection Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_selection_owner",
                "selection_owner@test.com",
                "Selection Owner",
                "owner"));

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
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Selection Corp", "selection@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project = new Project("Selection Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);

                      customerProjectRepository.save(
                          new CustomerProject(customerId, project.getId(), memberIdOwner));

                      var task =
                          new Task(
                              project.getId(),
                              "Selection Task",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);

                      // Time entry 1: 120 min at 1800/hr = 3600
                      var te1 =
                          new TimeEntry(
                              task.getId(),
                              memberIdOwner,
                              LocalDate.of(2026, 3, 10),
                              120,
                              true,
                              null,
                              "Work item 1");
                      te1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te1 = timeEntryRepository.save(te1);
                      timeEntryId1 = te1.getId();

                      // Time entry 2: 60 min at 1800/hr = 1800
                      var te2 =
                          new TimeEntry(
                              task.getId(),
                              memberIdOwner,
                              LocalDate.of(2026, 3, 15),
                              60,
                              true,
                              null,
                              "Work item 2");
                      te2.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te2 = timeEntryRepository.save(te2);
                      timeEntryId2 = te2.getId();

                      // Expense: 500 ZAR filing fee (billable, no markup)
                      var expense =
                          new Expense(
                              project.getId(),
                              memberIdOwner,
                              LocalDate.of(2026, 3, 12),
                              "Filing fee",
                              new BigDecimal("500.00"),
                              "ZAR",
                              ExpenseCategory.FILING_FEE);
                      expense = expenseRepository.save(expense);
                      expenseId = expense.getId();
                    }));

    // Create billing run and load preview
    billingRunId = createBillingRun("Selection Run", "2026-03-01", "2026-03-31", "ZAR", true);

    var previewResult =
        mockMvc
            .perform(
                post("/api/billing-runs/" + billingRunId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    itemId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.items[0].id");
  }

  @Test
  @Order(1)
  void updateSelection_excludeEntry_updatesIncludedFlag() throws Exception {
    mockMvc
        .perform(
            put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/selections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "selections": [
                        {"entryType": "TIME_ENTRY", "entryId": "%s", "included": false}
                      ]
                    }
                    """
                        .formatted(timeEntryId1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @Order(2)
  void updateSelection_recalculatesTotals() throws Exception {
    // After excluding timeEntry1 (120min * 1800/hr = 3600), only timeEntry2 remains (60min *
    // 1800/hr = 1800)
    var result =
        mockMvc
            .perform(
                put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/selections")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "selections": [
                            {"entryType": "TIME_ENTRY", "entryId": "%s", "included": false}
                          ]
                        }
                        """
                            .formatted(timeEntryId1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unbilledTimeCount").value(1))
            .andExpect(jsonPath("$.unbilledExpenseCount").value(1))
            .andReturn();
  }

  @Test
  @Order(3)
  void updateSelection_nonPreviewRun_returns400() throws Exception {
    // Create a new run and transition it to IN_PROGRESS
    String statusTestRunId =
        createBillingRun("Status Selection Test", "2026-03-01", "2026-03-31", "ZAR", false);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run =
                          billingRunRepository
                              .findById(UUID.fromString(statusTestRunId))
                              .orElseThrow();
                      run.startGeneration();
                      billingRunRepository.save(run);
                    }));

    mockMvc
        .perform(
            put("/api/billing-runs/"
                    + statusTestRunId
                    + "/items/00000000-0000-0000-0000-000000000001/selections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "selections": [
                        {"entryType": "TIME_ENTRY", "entryId": "%s", "included": false}
                      ]
                    }
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(4)
  void excludeCustomer_setsExcludedStatus() throws Exception {
    mockMvc
        .perform(
            put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/exclude")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId))
        .andExpect(jsonPath("$.status").value("EXCLUDED"));
  }

  @Test
  @Order(5)
  void includeCustomer_setsPendingStatus() throws Exception {
    // Item was excluded in test 4 — re-include it
    mockMvc
        .perform(
            put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/include")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(itemId))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @Order(6)
  void excludeCustomer_nonPreviewRun_returns400() throws Exception {
    String statusTestRunId =
        createBillingRun("Exclude Status Test", "2026-03-01", "2026-03-31", "ZAR", false);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run =
                          billingRunRepository
                              .findById(UUID.fromString(statusTestRunId))
                              .orElseThrow();
                      run.startGeneration();
                      billingRunRepository.save(run);
                    }));

    mockMvc
        .perform(
            put("/api/billing-runs/"
                    + statusTestRunId
                    + "/items/00000000-0000-0000-0000-000000000001/exclude")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(7)
  void updateSelection_entryNotFound_returns404() throws Exception {
    mockMvc
        .perform(
            put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/selections")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "selections": [
                        {"entryType": "TIME_ENTRY", "entryId": "00000000-0000-0000-0000-000000000099", "included": false}
                      ]
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(8)
  void updateSelection_multipleEntries_updatesAll() throws Exception {
    // First re-include timeEntry1 and exclude timeEntry2 + expense in one call
    var result =
        mockMvc
            .perform(
                put("/api/billing-runs/" + billingRunId + "/items/" + itemId + "/selections")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "selections": [
                            {"entryType": "TIME_ENTRY", "entryId": "%s", "included": true},
                            {"entryType": "TIME_ENTRY", "entryId": "%s", "included": false},
                            {"entryType": "EXPENSE", "entryId": "%s", "included": false}
                          ]
                        }
                        """
                            .formatted(timeEntryId1, timeEntryId2, expenseId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unbilledTimeCount").value(1))
            .andExpect(jsonPath("$.unbilledExpenseCount").value(0))
            .andReturn();
  }

  // --- Helpers ---

  private String createBillingRun(
      String name, String periodFrom, String periodTo, String currency, boolean includeExpenses)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_selection_owner"))
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
