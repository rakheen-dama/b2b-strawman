package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnbilledSummaryTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_unbilled_summary_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Unbilled Summary Test Org", null);

    memberIdOwner =
        UUID.fromString(
            syncMember("user_summary_owner", "summary_owner@test.com", "Summary Owner", "owner"));

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
                      // Active customer with prerequisite fields
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Summary Corp", "summary@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project linked to customer
                      var project = new Project("Summary Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Task
                      var task =
                          new Task(
                              projectId,
                              "Summary Task",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // Billable time entry in March 2026, ZAR
                      var te1 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 15),
                              120,
                              true,
                              null,
                              "ZAR work");
                      te1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      timeEntryRepository.save(te1);

                      // Second billable time entry in March, ZAR
                      var te2 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 20),
                              60,
                              true,
                              null,
                              "More ZAR work");
                      te2.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      timeEntryRepository.save(te2);

                      // Billable time entry in March, USD (for currency filter test)
                      var te3 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 16),
                              90,
                              true,
                              null,
                              "USD work");
                      te3.snapshotBillingRate(new BigDecimal("200.00"), "USD");
                      timeEntryRepository.save(te3);

                      // Billable time entry in April 2026, ZAR (for period filter test)
                      var te4 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 4, 10),
                              60,
                              true,
                              null,
                              "April work");
                      te4.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      timeEntryRepository.save(te4);

                      // Create a real invoice to reference for the billed time entry
                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Summary Corp",
                              "summary@test.com",
                              null,
                              "Unbilled Summary Test Org",
                              memberIdOwner);
                      invoice = invoiceRepository.saveAndFlush(invoice);

                      // Billed time entry (has invoiceId set) in March, ZAR
                      var te5 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 12),
                              30,
                              true,
                              null,
                              "Already billed");
                      te5.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te5.setInvoiceId(invoice.getId());
                      timeEntryRepository.save(te5);

                      // Billable expense in March, ZAR
                      var expense =
                          new Expense(
                              projectId,
                              memberIdOwner,
                              LocalDate.of(2026, 3, 18),
                              "Filing fee",
                              new BigDecimal("500.00"),
                              "ZAR",
                              ExpenseCategory.FILING_FEE);
                      expenseRepository.save(expense);
                    }));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_summary_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_summary_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
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
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  @Test
  @Order(1)
  void unbilledSummary_returnsCustomersWithUnbilledWork() throws Exception {
    // Query March 2026, ZAR — should find 2 unbilled time entries + 1 expense
    // te1: 120min * 1800/60 = 3600, te2: 60min * 1800/60 = 1800 => total time = 5400
    // expense: 500.00 (no markup) => total = 5900
    mockMvc
        .perform(
            get("/api/invoices/unbilled-summary")
                .param("periodFrom", "2026-03-01")
                .param("periodTo", "2026-03-31")
                .param("currency", "ZAR")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].customerId").value(customerId.toString()))
        .andExpect(jsonPath("$[0].customerName").value("Summary Corp"))
        .andExpect(jsonPath("$[0].customerEmail").value("summary@test.com"))
        .andExpect(jsonPath("$[0].unbilledTimeEntryCount").value(2))
        .andExpect(jsonPath("$[0].unbilledTimeAmount").value(5400.00))
        .andExpect(jsonPath("$[0].unbilledExpenseCount").value(1))
        .andExpect(jsonPath("$[0].unbilledExpenseAmount").value(500.00))
        .andExpect(jsonPath("$[0].totalUnbilledAmount").value(5900.00));
  }

  @Test
  @Order(2)
  void unbilledSummary_excludesBilledEntries() throws Exception {
    // te5 has invoiceId set — it should NOT appear in unbilled counts
    // Only te1 and te2 should be counted (not te5 which is billed)
    mockMvc
        .perform(
            get("/api/invoices/unbilled-summary")
                .param("periodFrom", "2026-03-01")
                .param("periodTo", "2026-03-31")
                .param("currency", "ZAR")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].unbilledTimeEntryCount").value(2));
  }

  @Test
  @Order(3)
  void unbilledSummary_filtersPerCurrency() throws Exception {
    // Query for USD — should find te3 only (90min * 200/60 = 300)
    mockMvc
        .perform(
            get("/api/invoices/unbilled-summary")
                .param("periodFrom", "2026-03-01")
                .param("periodTo", "2026-03-31")
                .param("currency", "USD")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].unbilledTimeEntryCount").value(1))
        .andExpect(jsonPath("$[0].unbilledTimeAmount").value(300.00))
        .andExpect(jsonPath("$[0].unbilledExpenseCount").value(0))
        .andExpect(jsonPath("$[0].unbilledExpenseAmount").value(0.00));
  }

  @Test
  @Order(4)
  void unbilledSummary_filtersPerPeriod() throws Exception {
    // Query April 2026, ZAR — should find te4 only (60min * 1800/60 = 1800)
    mockMvc
        .perform(
            get("/api/invoices/unbilled-summary")
                .param("periodFrom", "2026-04-01")
                .param("periodTo", "2026-04-30")
                .param("currency", "ZAR")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].unbilledTimeEntryCount").value(1))
        .andExpect(jsonPath("$[0].unbilledTimeAmount").value(1800.00))
        .andExpect(jsonPath("$[0].unbilledExpenseCount").value(0));
  }

  @Test
  @Order(5)
  void unbilledSummary_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/unbilled-summary")
                .param("periodFrom", "2026-03-01")
                .param("periodTo", "2026-03-31")
                .param("currency", "ZAR")
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }
}
