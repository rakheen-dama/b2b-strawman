package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.expense.Expense;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseCategory;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
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
import org.springframework.data.domain.PageRequest;
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
class BillingRunCancelTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_cancel_test";

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
  @Autowired private AuditEventRepository auditEventRepository;
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
    provisioningService.provisionTenant(ORG_ID, "Cancel Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_cancel_owner",
                "cancel_owner@test.com",
                "Cancel Owner",
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
                              "Cancel Corp", "cancel@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var customer2 =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Cancel Second Corp", "cancel2@test.com", memberIdOwner);
                      customer2 = customerRepository.save(customer2);
                      customerId2 = customer2.getId();

                      var project = new Project("Cancel Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var project2 =
                          new Project("Cancel Project 2", "Test project 2", memberIdOwner);
                      project2 = projectRepository.save(project2);
                      projectId2 = project2.getId();
                      customerProjectRepository.save(
                          new CustomerProject(customerId2, projectId2, memberIdOwner));

                      var task =
                          new Task(
                              projectId,
                              "Cancel Task",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      var task2 =
                          new Task(
                              projectId2,
                              "Cancel Task 2",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task2 = taskRepository.save(task2);
                      taskId2 = task2.getId();
                    }));
  }

  private void seedUnbilledEntries(String label, boolean includeExpenses) {
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
  void cancelPreview_deletesRunAndItems() throws Exception {
    seedUnbilledEntries("cancelPreview", false);
    String runId = createBillingRunWithPreview("Cancel Preview Run", false);

    // Cancel the PREVIEW run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify the run is gone
    mockMvc
        .perform(
            get("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(2)
  void cancelCompleted_voidsDraftInvoices() throws Exception {
    seedUnbilledEntries("cancelVoid", false);
    String runId = createBillingRunWithPreview("Cancel Void Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Cancel the COMPLETED run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify DRAFT invoices are now VOID
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.VOID);
                      assertThat(invoices).isNotEmpty();

                      var draftInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.DRAFT);
                      assertThat(draftInvoices).isEmpty();
                    }));
  }

  @Test
  @Order(3)
  void cancelCompleted_unbillsTimeEntries() throws Exception {
    seedUnbilledEntries("cancelUnbill", false);
    String runId = createBillingRunWithPreview("Cancel Unbill Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk());

    // Verify time entries are billed (have invoiceId)
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
                      assertThat(items).isNotEmpty();
                      for (var item : items) {
                        assertThat(item.getInvoiceId()).isNotNull();
                      }
                    }));

    // Cancel the run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify time entries are unbilled (invoiceId is null)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Find time entries by task — they should be unbilled now
                      var entries = timeEntryRepository.findByTaskId(taskId);
                      var unbilledCancel =
                          entries.stream()
                              .filter(
                                  te ->
                                      te.getDescription() != null
                                          && te.getDescription().contains("cancelUnbill"))
                              .toList();
                      for (var entry : unbilledCancel) {
                        assertThat(entry.getInvoiceId()).isNull();
                      }
                    }));
  }

  @Test
  @Order(4)
  void cancelCompleted_marksRunCancelled() throws Exception {
    seedUnbilledEntries("cancelStatus", false);
    String runId = createBillingRunWithPreview("Cancel Status Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk());

    // Cancel the run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify the run status is CANCELLED
    mockMvc
        .perform(
            get("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  @Order(5)
  void cancelCompleted_preservesApprovedInvoices() throws Exception {
    seedUnbilledEntries("cancelPreserve", false);
    String runId = createBillingRunWithPreview("Cancel Preserve Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk());

    // Approve one invoice
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var draftInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.DRAFT);
                      assertThat(draftInvoices).hasSizeGreaterThanOrEqualTo(2);

                      // Approve the first invoice
                      var firstInvoice = draftInvoices.getFirst();
                      firstInvoice.approve("INV-CANCEL-001", memberIdOwner);
                      invoiceRepository.save(firstInvoice);
                    }));

    // Cancel the run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify: approved invoice is preserved, other(s) are voided
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
                      assertThat(approvedInvoices).hasSize(1);

                      var voidInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.VOID);
                      assertThat(voidInvoices).hasSize(1);

                      var draftInvoices =
                          invoiceRepository.findByBillingRunIdAndStatus(
                              UUID.fromString(runId), InvoiceStatus.DRAFT);
                      assertThat(draftInvoices).isEmpty();
                    }));
  }

  @Test
  @Order(6)
  void cancel_logsAuditEvent() throws Exception {
    seedUnbilledEntries("cancelAudit", false);
    String runId = createBillingRunWithPreview("Cancel Audit Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk());

    // Cancel the run
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Verify audit event exists
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var events =
                          auditEventRepository.findByFilter(
                              "billing_run",
                              UUID.fromString(runId),
                              null,
                              "billing_run.cancelled",
                              null,
                              null,
                              PageRequest.of(0, 10));
                      assertThat(events.getContent()).isNotEmpty();
                    }));
  }

  @Test
  @Order(7)
  void cancelCancelled_throwsInvalidState() throws Exception {
    seedUnbilledEntries("cancelDouble", false);
    String runId = createBillingRunWithPreview("Cancel Double Run", false);

    // Generate invoices
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isOk());

    // First cancel — should succeed
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isNoContent());

    // Second cancel — should fail with 400 (InvalidStateException)
    mockMvc
        .perform(
            delete("/api/billing-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner")))
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---

  private String createBillingRunWithPreview(String name, boolean includeExpenses)
      throws Exception {
    String runId = createBillingRun(name, "2026-03-01", "2026-03-31", "ZAR", includeExpenses);

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_cancel_owner"))
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
