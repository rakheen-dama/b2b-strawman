package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
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
class BillingRunBatchOperationsTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_batch_ops_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private BillingRunRepository billingRunRepository;
  @Autowired private BillingRunItemRepository billingRunItemRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID customerId2;
  private UUID projectId;
  private UUID projectId2;
  private UUID taskId;
  private UUID taskId2;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Batch Ops Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_batch_owner",
                "batch_owner@test.com",
                "Batch Owner",
                "owner"));

    TestModuleHelper.enableModules(mockMvc, ORG_ID, "user_batch_owner", "bulk_billing");

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
                              "Batch Corp", "batch@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var customer2 =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Batch Second Corp", "batch2@test.com", memberIdOwner);
                      customer2 = customerRepository.save(customer2);
                      customerId2 = customer2.getId();

                      var project = new Project("Batch Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var project2 =
                          new Project("Batch Project 2", "Test project 2", memberIdOwner);
                      project2 = projectRepository.save(project2);
                      projectId2 = project2.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId2, projectId2, memberIdOwner));

                      var task =
                          new Task(
                              projectId, "Batch Task", null, "MEDIUM", "TASK", null, memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      var task2 =
                          new Task(
                              projectId2,
                              "Batch Task 2",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task2 = taskRepository.save(task2);
                      taskId2 = task2.getId();
                    }));
  }

  @Test
  @Order(1)
  void batchApprove_approvesAllDraftInvoices() throws Exception {
    seedUnbilledEntries("approve_all");
    String runId = createAndGenerateBillingRun("Approve All Run");

    // Approve all invoices
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/approve")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
            .andExpect(status().isOk())
            .andReturn();

    int successCount = JsonPath.read(result.getResponse().getContentAsString(), "$.successCount");
    assertThat(successCount).isGreaterThan(0);

    // Verify invoices are APPROVED
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var approvedInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.APPROVED);
                      assertThat(approvedInvoices).isNotEmpty();
                    }));
  }

  @Test
  @Order(2)
  void batchApprove_returnsSuccessCount() throws Exception {
    seedUnbilledEntries("approve_count");
    String runId = createAndGenerateBillingRun("Approve Count Run");

    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/approve")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").isNumber())
            .andExpect(jsonPath("$.failureCount").value(0))
            .andExpect(jsonPath("$.failures").isArray())
            .andReturn();

    int successCount = JsonPath.read(result.getResponse().getContentAsString(), "$.successCount");
    assertThat(successCount).isGreaterThan(0);
  }

  @Test
  @Order(3)
  void batchApprove_onNonCompletedRun_returns400() throws Exception {
    seedUnbilledEntries("approve_preview");
    // Create a run but don't generate (stays in PREVIEW)
    String runId = createBillingRunWithPreview("Approve Preview Run");

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(4)
  void batchSend_sendsApprovedInvoices() throws Exception {
    seedUnbilledEntries("send_all");
    String runId = createAndGenerateBillingRun("Send All Run");

    // First approve
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
        .andExpect(status().isOk());

    // Then send
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/send")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    int successCount = JsonPath.read(result.getResponse().getContentAsString(), "$.successCount");
    assertThat(successCount).isGreaterThan(0);

    // Verify invoices are SENT
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var sentInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.SENT);
                      assertThat(sentInvoices).isNotEmpty();
                    }));
  }

  @Test
  @Order(5)
  void batchSend_appliesDefaultDueDate() throws Exception {
    seedUnbilledEntries("send_due_date");
    String runId = createAndGenerateBillingRun("Send Due Date Run");

    // Approve first
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
        .andExpect(status().isOk());

    // Send with default due date
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultDueDate": "2026-04-30",
                      "defaultPaymentTerms": "Net 30"
                    }
                    """))
        .andExpect(status().isOk());

    // Verify invoices have due date set
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var sentInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.SENT);
                      for (var invoice : sentInvoices) {
                        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 30));
                        assertThat(invoice.getPaymentTerms()).isEqualTo("Net 30");
                      }
                    }));
  }

  @Test
  @Order(6)
  void batchSend_rateLimitsAndUpdatesTotalSent() throws Exception {
    seedUnbilledEntries("send_rate");
    String runId = createAndGenerateBillingRun("Send Rate Run");

    // Approve first
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
        .andExpect(status().isOk());

    // Send
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    // Verify totalSent is updated on the billing run
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var freshRun =
                          billingRunRepository.findById(UUID.fromString(runId)).orElseThrow();
                      assertThat(freshRun.getTotalSent()).isGreaterThan(0);
                    }));
  }

  @Test
  @Order(7)
  void batchSend_onEmptyApprovedList_returnsZero() throws Exception {
    seedUnbilledEntries("send_empty");
    String runId = createAndGenerateBillingRun("Send Empty Run");

    // Don't approve — send directly (no APPROVED invoices)
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/send")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

    int successCount = JsonPath.read(result.getResponse().getContentAsString(), "$.successCount");
    int failureCount = JsonPath.read(result.getResponse().getContentAsString(), "$.failureCount");
    assertThat(successCount).isZero();
    assertThat(failureCount).isZero();
  }

  // partition() was inlined into BillingRunService — test removed (method no longer public)

  // --- Helpers ---

  private void seedUnbilledEntries(String label) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
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

  private String createAndGenerateBillingRun(String name) throws Exception {
    String runId = createBillingRunWithPreview(name);

    // Generate
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner")))
        .andExpect(status().isOk());

    return runId;
  }

  private String createBillingRunWithPreview(String name) throws Exception {
    String runId = createBillingRun(name, "2026-03-01", "2026-03-31", "ZAR");

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    return runId;
  }

  private String createBillingRun(String name, String periodFrom, String periodTo, String currency)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "periodFrom": "%s",
                          "periodTo": "%s",
                          "currency": "%s",
                          "includeExpenses": false,
                          "includeRetainers": false
                        }
                        """
                            .formatted(name, periodFrom, periodTo, currency)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
