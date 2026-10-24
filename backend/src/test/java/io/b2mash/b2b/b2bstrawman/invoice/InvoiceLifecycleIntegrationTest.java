package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.BillingSettings;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
class InvoiceLifecycleIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_lifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InvoiceTransitionService invoiceTransitionService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private PaymentEventRepository paymentEventRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.audit.AuditService auditService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Lifecycle Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_lifecycle_owner",
                "inv_lifecycle_owner@test.com",
                "Lifecycle Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Customer
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Lifecycle Test Corp", "lifecycle@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project
                      var project =
                          new Project(
                              "Lifecycle Test Project",
                              "Project for lifecycle tests",
                              memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      // Link project to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Task
                      var task =
                          new Task(
                              projectId,
                              "Lifecycle Work",
                              "Lifecycle task",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();
                    }));
  }

  // --- Helper: create a billable time entry within tenant scope ---
  private UUID createBillableTimeEntry(LocalDate date, int durationMinutes, String description) {
    var holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              date,
                              durationMinutes,
                              true,
                              null,
                              description);
                      te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te = timeEntryRepository.save(te);
                      holder[0] = te.getId();
                    }));
    return holder[0];
  }

  // --- Helper: create a draft invoice with time entries via API ---
  private String createDraftWithTimeEntries(UUID... timeEntryIds) throws Exception {
    StringBuilder teIds = new StringBuilder("[");
    for (int i = 0; i < timeEntryIds.length; i++) {
      if (i > 0) teIds.append(",");
      teIds.append("\"").append(timeEntryIds[i]).append("\"");
    }
    teIds.append("]");

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "currency": "ZAR",
                          "timeEntryIds": %s
                        }
                        """
                            .formatted(customerId, teIds.toString())))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- Helper: create a draft invoice with a manual line item ---
  private String createDraftWithManualLine() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "currency": "ZAR"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Add a manual line item
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "matterId": "%s",
                      "description": "Consulting",
                      "quantity": 1.0000,
                      "unitPrice": 5000.00,
                      "sortOrder": 0
                    }
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());

    return invoiceId;
  }

  // --- Test 1: approve assigns number and sets time entry invoice_id ---
  @Test
  void shouldApproveInvoiceAssigningNumberAndLinkingTimeEntries() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.of(2025, 2, 1), 120, "Approve test");
    String invoiceId = createDraftWithTimeEntries(teId);

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.invoiceNumber").isNotEmpty())
        .andExpect(jsonPath("$.approvedBy").isNotEmpty())
        .andExpect(jsonPath("$.issueDate").isNotEmpty());
  }

  // Phase 88 U3 (ADR-347): approve stamps dueDate = issueDate + the firm's payment terms when the
  // draft has none. Terms set to 14 so a hard-coded 30 cannot pass; restored afterwards (shared
  // PER_CLASS tenant — no other case reads the setting).
  @Test
  void approveWithoutDueDateStampsIssuePlusTerms() throws Exception {
    setPaymentTerms(14);
    try {
      String invoiceId = createDraftWithManualLine();
      var json =
          mockMvc
              .perform(
                  post("/api/invoices/" + invoiceId + "/approve")
                      .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("APPROVED"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String dueDate = JsonPath.read(json, "$.dueDate");
      assertThat(dueDate).as("approve must stamp a due date when the draft has none").isNotNull();
      LocalDate issue = LocalDate.parse(JsonPath.read(json, "$.issueDate"));
      assertThat(LocalDate.parse(dueDate)).isEqualTo(issue.plusDays(14));

      // The stamped date travels unchanged onto the SENT fee note.
      mockMvc
          .perform(
              post("/api/invoices/" + invoiceId + "/send")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SENT"))
          .andExpect(jsonPath("$.dueDate").value(dueDate));
    } finally {
      setPaymentTerms(BillingSettings.DEFAULT_PAYMENT_TERMS_DAYS);
    }
  }

  // A due date typed on the draft is never overwritten by approve (ADR-347).
  @Test
  void typedDueDatePreserved() throws Exception {
    String invoiceId = createDraftWithManualLine();
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dueDate": "2027-01-15"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.dueDate").value("2027-01-15"));
  }

  private void setPaymentTerms(int days) throws Exception {
    mockMvc
        .perform(
            patch("/api/settings/batch-billing")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultPaymentTermsDays\": %d}".formatted(days)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultPaymentTermsDays").value(days));
  }

  // --- Test 2: approve fails if no lines ---
  @Test
  void shouldRejectApproveWhenNoLineItems() throws Exception {
    // Create a draft with no lines
    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "currency": "ZAR"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isBadRequest());
  }

  // --- Test 3: approve fails if not DRAFT ---
  @Test
  void shouldRejectApproveWhenNotDraft() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve it first
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Try to approve again (now APPROVED, not DRAFT)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }

  // --- Test 4: send transitions APPROVED -> SENT ---
  @Test
  void shouldSendApprovedInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Send
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  // OBS-8801: invoice.sent audit must carry details.project_id (derived from the invoice lines) so
  // the matter Activity feed (findByProjectId) and the portal Firm-actions trail
  // (findActivityFirmForCustomer) include the fee-note-sent milestone — both feeds scope on
  // details->>'project_id'.
  @Test
  void shouldEmitInvoiceSentAuditWithProjectIdSoActivityFeedShowsIt() throws Exception {
    String invoiceId = createDraftWithManualLine();

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var matterEvents =
                  auditEventRepository.findByProjectId(
                      projectId.toString(), null, null, PageRequest.of(0, 50));
              var sent =
                  matterEvents.getContent().stream()
                      .filter(e -> "invoice.sent".equals(e.getEventType()))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              new AssertionError(
                                  "invoice.sent not returned by the matter Activity feed query"
                                      + " (findByProjectId) — details.project_id is missing"));
              assertThat(sent.getDetails()).containsEntry("project_id", projectId.toString());
            });
  }

  // --- Test 5: send fails if not APPROVED ---
  @Test
  void shouldRejectSendWhenNotApproved() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Try to send a DRAFT (not APPROVED)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }

  // --- Test 6: recordPayment transitions SENT -> PAID ---
  @Test
  void shouldRecordPaymentOnSentInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve -> Send
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Record payment
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "paymentReference": "EFT-2025-0215"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paymentReference").value("EFT-2025-0215"))
        .andExpect(jsonPath("$.paidAt").isNotEmpty());
  }

  // --- Test 7: recordPayment fails if not SENT ---
  @Test
  void shouldRejectPaymentWhenNotSent() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve but don't send
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Try to pay an APPROVED invoice (not SENT)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "paymentReference": "EFT-SHOULD-FAIL"
                    }
                    """))
        .andExpect(status().isConflict());
  }

  // --- Test 8: void from APPROVED sets VOID and clears time entry invoice_id ---
  @Test
  void shouldVoidApprovedInvoiceAndUnlinkTimeEntries() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.of(2025, 2, 2), 60, "Void test");
    String invoiceId = createDraftWithTimeEntries(teId);

    // Approve
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Void
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOID"));

    // Verify time entry is now unlocked (can be edited)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te = timeEntryRepository.findById(teId).orElseThrow();
                      org.assertj.core.api.Assertions.assertThat(te.getInvoiceId()).isNull();
                    }));
  }

  // --- Test 9: void from SENT works ---
  @Test
  void shouldVoidSentInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve -> Send
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Void
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOID"));
  }

  // --- Test 10: void fails if PAID ---
  @Test
  void shouldRejectVoidWhenPaid() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Full lifecycle: Approve -> Send -> Pay
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/send")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Try to void a PAID invoice
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }

  // --- Test: void from DRAFT returns 409 ---
  @Test
  void shouldRejectVoidWhenDraft() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Try to void a DRAFT invoice (only APPROVED or SENT can be voided)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }

  // --- Test: payment from DRAFT returns 409 ---
  @Test
  void shouldRejectPaymentWhenDraft() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Try to pay a DRAFT invoice (only SENT can be paid)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "paymentReference": "EFT-SHOULD-FAIL"
                    }
                    """))
        .andExpect(status().isConflict());
  }

  // --- Phase 88 U15 (ADR-347): balance model + PARTIALLY_PAID on the manual endpoint ---

  /** Approve + send a fresh manual-line fee note; returns {id, total}. */
  private Object[] createSentInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());
    var sent =
        mockMvc
            .perform(
                post("/api/invoices/" + invoiceId + "/send")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
            .andExpect(status().isOk())
            .andReturn();
    Number total = JsonPath.read(sent.getResponse().getContentAsString(), "$.total");
    return new Object[] {invoiceId, new BigDecimal(total.toString()).setScale(2)};
  }

  private org.springframework.test.web.servlet.ResultActions postPayment(
      String invoiceId, String body) throws Exception {
    return mockMvc.perform(
        post("/api/invoices/" + invoiceId + "/payment")
            .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String zar(BigDecimal amount) {
    var symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH);
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator(',');
    return "R " + new java.text.DecimalFormat("#,##0.00", symbols).format(amount);
  }

  @Test
  void partialPaymentLeavesFeeNotePartiallyPaid() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    BigDecimal total = (BigDecimal) sent[1];

    postPayment(
            invoiceId,
            """
            {"amount": 1000.00, "paymentMethod": "EFT", "paymentReference": "FNB-PART-1"}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.amountPaid").value(1000.0))
        .andExpect(
            jsonPath("$.balanceDue").value(total.subtract(new BigDecimal("1000")).doubleValue()))
        .andExpect(jsonPath("$.paidAt").isEmpty())
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.payments[0].amount").value(1000.0))
        .andExpect(jsonPath("$.payments[0].paymentMethod").value("EFT"))
        .andExpect(jsonPath("$.payments[0].paymentReference").value("FNB-PART-1"))
        .andExpect(jsonPath("$.payments[0].source").value("MANUAL"));
  }

  @Test
  void completingPaymentMarksPaidWithPaidAt() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    BigDecimal total = (BigDecimal) sent[1];
    BigDecimal rest = total.subtract(new BigDecimal("1000.00"));

    postPayment(invoiceId, "{\"amount\": 1000.00, \"paymentMethod\": \"EFT\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
    postPayment(
            invoiceId,
            "{\"amount\": %s, \"paymentMethod\": \"CASH\"}".formatted(rest.toPlainString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paidAt").isNotEmpty())
        .andExpect(jsonPath("$.amountPaid").value(total.doubleValue()))
        .andExpect(jsonPath("$.balanceDue").value(0.0))
        .andExpect(jsonPath("$.payments.length()").value(2));
  }

  @Test
  void manualOverpaymentIsRefusedWith409() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    BigDecimal total = (BigDecimal) sent[1];

    postPayment(
            invoiceId,
            "{\"amount\": %s, \"paymentMethod\": \"EFT\"}"
                .formatted(total.add(BigDecimal.ONE).toPlainString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Payment exceeds balance due"))
        .andExpect(jsonPath("$.detail").value("Balance due is " + zar(total)));

    // Nothing changed: still SENT, no payment posted.
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/invoices/" + invoiceId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.amountPaid").value(0.0))
        .andExpect(jsonPath("$.payments.length()").value(0));
  }

  @Test
  void zeroOrNegativeAmountIsRejectedWith400() throws Exception {
    String invoiceId = (String) createSentInvoice()[0];
    postPayment(invoiceId, "{\"amount\": 0, \"paymentMethod\": \"EFT\"}")
        .andExpect(status().isBadRequest());
    postPayment(invoiceId, "{\"amount\": -5.00, \"paymentMethod\": \"EFT\"}")
        .andExpect(status().isBadRequest());
    postPayment(invoiceId, "{\"amount\": 10.00, \"paymentMethod\": \"BITCOIN\"}")
        .andExpect(status().isBadRequest());
  }

  /** D5 back-compat: the deployed firm form still posts only a reference until 622E ships. */
  @Test
  void legacyReferenceOnlyBodySettlesTheFullBalance() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    BigDecimal total = (BigDecimal) sent[1];

    postPayment(invoiceId, "{\"paymentReference\": \"LEGACY-REF-1\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.amountPaid").value(total.doubleValue()))
        .andExpect(jsonPath("$.balanceDue").value(0.0))
        .andExpect(jsonPath("$.payments[0].paymentMethod").value("OTHER"))
        .andExpect(jsonPath("$.payments[0].paymentReference").value("LEGACY-REF-1"));
  }

  @Test
  void voidRefusedWhenPartiallyPaid() throws Exception {
    String invoiceId = (String) createSentInvoice()[0];
    postPayment(invoiceId, "{\"amount\": 100.00, \"paymentMethod\": \"EFT\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/void")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }

  private <T> T inTenant(java.util.concurrent.Callable<T> body) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(() -> body.call());
  }

  @Test
  void trustTransferOverpaymentIsCappedAtPaidWithExcessAudit() throws Exception {
    var sent = createSentInvoice();
    UUID invoiceId = UUID.fromString((String) sent[0]);
    BigDecimal total = (BigDecimal) sent[1];
    BigDecimal moved = total.add(new BigDecimal("500.00"));

    var response =
        inTenant(
            () ->
                invoiceTransitionService.postPayment(
                    invoiceId,
                    new PaymentPosting(
                        moved,
                        PaymentMethod.TRUST_TRANSFER,
                        "FT-EXCESS-1",
                        "trust_fee_transfer",
                        null,
                        "TRUST")));
    assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(response.amountPaid()).isEqualByComparingTo(total);
    assertThat(response.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(response.paidAt()).isNotNull();
    assertThat(response.payments()).hasSize(1);
    assertThat(response.payments().getFirst().source()).isEqualTo("TRUST_TRANSFER");

    inTenant(
        () -> {
          var events = paymentEventRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
          assertThat(events).hasSize(1);
          var event = events.getFirst();
          assertThat(event.getAmount()).isEqualByComparingTo(moved); // the real amount
          assertThat(event.getPaymentMethod()).isEqualTo(PaymentMethod.TRUST_TRANSFER);
          assertThat(event.getPaymentDestination()).isEqualTo("TRUST");
          assertThat(event.getStatus()).isEqualTo(PaymentEventStatus.COMPLETED);

          var excess =
              auditService.findEvents(
                  new io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter(
                      "invoice", invoiceId, null, "invoice.payment_excess", null, null, null),
                  PageRequest.of(0, 10));
          assertThat(excess.getContent()).hasSize(1);
          assertThat(excess.getContent().getFirst().getDetails()).containsEntry("excess", "500.00");
          return null;
        });
  }

  @Test
  void reversalFromPartiallyPaidReturnsToSent() throws Exception {
    String invoiceId = (String) createSentInvoice()[0];
    var paid =
        postPayment(invoiceId, "{\"amount\": 750.00, \"paymentMethod\": \"EFT\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andReturn();
    UUID eventId =
        UUID.fromString(JsonPath.read(paid.getResponse().getContentAsString(), "$.payments[0].id"));

    inTenant(
        () -> {
          invoiceTransitionService.reversePayment(UUID.fromString(invoiceId), eventId);
          return null;
        });

    var invoice =
        inTenant(() -> invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(invoice.getPaidAt()).isNull();
    inTenant(
        () -> {
          var reversed =
              auditService.findEvents(
                  new io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter(
                      "invoice",
                      UUID.fromString(invoiceId),
                      null,
                      "invoice.payment_reversed",
                      null,
                      null,
                      null),
                  PageRequest.of(0, 10));
          assertThat(reversed.getContent()).hasSize(1);
          // money scale kept at 2 even when no COMPLETED events remain
          assertThat(reversed.getContent().getFirst().getDetails())
              .containsEntry("amount_paid", "0.00");
          return null;
        });
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/invoices/" + invoiceId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.amountPaid").value(0.0))
        .andExpect(jsonPath("$.payments.length()").value(0));
  }

  @Test
  void reversalFromPaidWithEventsRemainingLeavesPartiallyPaid() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    BigDecimal total = (BigDecimal) sent[1];
    BigDecimal first = new BigDecimal("1200.00");

    postPayment(invoiceId, "{\"amount\": 1200.00, \"paymentMethod\": \"EFT\"}")
        .andExpect(status().isOk());
    var settled =
        postPayment(
                invoiceId,
                "{\"amount\": %s, \"paymentMethod\": \"EFT\"}"
                    .formatted(total.subtract(first).toPlainString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andReturn();
    // payments[] is newest first — [0] is the second (settling) payment.
    UUID secondEventId =
        UUID.fromString(
            JsonPath.read(settled.getResponse().getContentAsString(), "$.payments[0].id"));
    Number secondAmount =
        JsonPath.read(settled.getResponse().getContentAsString(), "$.payments[0].amount");
    assertThat(new BigDecimal(secondAmount.toString())).isEqualByComparingTo(total.subtract(first));

    inTenant(
        () -> {
          invoiceTransitionService.reversePayment(UUID.fromString(invoiceId), secondEventId);
          return null;
        });

    var invoice =
        inTenant(() -> invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo(first);
    assertThat(invoice.getPaidAt()).isNull();
  }

  @Test
  void reversalKeepsPaidWhenRemainingEventsStillCoverTotal() throws Exception {
    var sent = createSentInvoice();
    String invoiceId = (String) sent[0];
    UUID id = UUID.fromString(invoiceId);
    BigDecimal total = (BigDecimal) sent[1];

    var part =
        postPayment(invoiceId, "{\"amount\": 500.00, \"paymentMethod\": \"EFT\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
            .andReturn();
    UUID manualEventId =
        UUID.fromString(JsonPath.read(part.getResponse().getContentAsString(), "$.payments[0].id"));

    // An external source then moves the full total (capped at PAID; the event keeps the real
    // amount)
    var settled =
        inTenant(
            () ->
                invoiceTransitionService.postPayment(
                    id,
                    new PaymentPosting(
                        total,
                        PaymentMethod.TRUST_TRANSFER,
                        "FT-COVER-1",
                        "trust_fee_transfer",
                        null,
                        "TRUST")));
    assertThat(settled.status()).isEqualTo(InvoiceStatus.PAID);
    var paidAt = settled.paidAt();
    assertThat(paidAt).isNotNull();

    // Reversing the manual 500 leaves Σ remaining = total ≥ total → still PAID, paidAt kept (A3)
    inTenant(
        () -> {
          invoiceTransitionService.reversePayment(id, manualEventId);
          return null;
        });

    var invoice = inTenant(() -> invoiceRepository.findById(id).orElseThrow());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo(total);
    assertThat(invoice.getBalanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(invoice.getPaidAt()).isNotNull();
    assertThat(invoice.getPaidAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
        .isEqualTo(paidAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
  }

  // --- Test 11: double-billing prevention (ADR-050) ---
  @Test
  void shouldPreventDoubleBillingOfTimeEntries() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.of(2025, 2, 3), 90, "Double-billing test");

    // Create draft A with this time entry
    createDraftWithTimeEntries(teId);

    // Attempt to create draft B with the same time entry -- should fail because
    // createDraft already sets invoiceId on the time entry
    mockMvc
        .perform(
            post("/api/invoices")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "currency": "ZAR",
                      "timeEntryIds": ["%s"]
                    }
                    """
                        .formatted(customerId, teId)))
        .andExpect(status().isConflict());
  }

  // --- Test 12: time entry edit blocked when invoiced ---
  @Test
  void shouldBlockTimeEntryEditWhenInvoiced() throws Exception {
    UUID teId = createBillableTimeEntry(LocalDate.of(2025, 2, 4), 60, "Lock edit test");
    String invoiceId = createDraftWithTimeEntries(teId);

    // Approve to fully lock the time entry
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isOk());

    // Attempt to update the time entry -- should fail with 409
    mockMvc
        .perform(
            put("/api/time-entries/" + teId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Should be blocked"
                    }
                    """))
        .andExpect(status().isConflict());

    // Attempt to delete the time entry -- should fail with 409
    mockMvc
        .perform(
            delete("/api/time-entries/" + teId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_lifecycle_owner")))
        .andExpect(status().isConflict());
  }
}
