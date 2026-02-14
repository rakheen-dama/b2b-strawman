package io.b2mash.b2b.b2bstrawman.invoice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
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
class InvoiceLifecycleIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_lifecycle_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Lifecycle Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
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
                          new Customer(
                              "Lifecycle Test Corp",
                              "lifecycle@test.com",
                              "+1-555-0600",
                              "LTC-001",
                              "Test customer",
                              memberIdOwner);
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
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
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "projectId": "%s",
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
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.invoiceNumber").isNotEmpty())
        .andExpect(jsonPath("$.approvedBy").isNotEmpty())
        .andExpect(jsonPath("$.issueDate").isNotEmpty());
  }

  // --- Test 2: approve fails if no lines ---
  @Test
  void shouldRejectApproveWhenNoLineItems() throws Exception {
    // Create a draft with no lines
    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "currency": "ZAR"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Test 3: approve fails if not DRAFT ---
  @Test
  void shouldRejectApproveWhenNotDraft() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve it first
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Try to approve again (now APPROVED, not DRAFT)
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // --- Test 4: send transitions APPROVED -> SENT ---
  @Test
  void shouldSendApprovedInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Send
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  // --- Test 5: send fails if not APPROVED ---
  @Test
  void shouldRejectSendWhenNotApproved() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Try to send a DRAFT (not APPROVED)
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // --- Test 6: recordPayment transitions SENT -> PAID ---
  @Test
  void shouldRecordPaymentOnSentInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve -> Send
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isOk());

    // Record payment
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(ownerJwt())
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
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Try to pay an APPROVED invoice (not SENT)
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payment")
                .with(ownerJwt())
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
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Void
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
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
                      var te = timeEntryRepository.findOneById(teId).orElseThrow();
                      org.assertj.core.api.Assertions.assertThat(te.getInvoiceId()).isNull();
                    }));
  }

  // --- Test 9: void from SENT works ---
  @Test
  void shouldVoidSentInvoice() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Approve -> Send
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isOk());

    // Void
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOID"));
  }

  // --- Test 10: void fails if PAID ---
  @Test
  void shouldRejectVoidWhenPaid() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Full lifecycle: Approve -> Send -> Pay
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(ownerJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/payment").with(ownerJwt()))
        .andExpect(status().isOk());

    // Try to void a PAID invoice
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // --- Test: void from DRAFT returns 409 ---
  @Test
  void shouldRejectVoidWhenDraft() throws Exception {
    String invoiceId = createDraftWithManualLine();

    // Try to void a DRAFT invoice (only APPROVED or SENT can be voided)
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/void").with(ownerJwt()))
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "paymentReference": "EFT-SHOULD-FAIL"
                    }
                    """))
        .andExpect(status().isConflict());
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
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
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk());

    // Attempt to update the time entry -- should fail with 409
    mockMvc
        .perform(
            put("/api/time-entries/" + teId)
                .with(ownerJwt())
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
        .perform(delete("/api/time-entries/" + teId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_lifecycle_owner")
                    .claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Helpers ---

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
