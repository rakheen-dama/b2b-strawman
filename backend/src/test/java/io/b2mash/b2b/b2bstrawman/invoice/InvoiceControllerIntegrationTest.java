package io.b2mash.b2b.b2bstrawman.invoice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class InvoiceControllerIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_ctrl_test";

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
  private UUID timeEntryId1;
  private UUID timeEntryId2;
  private UUID nonBillableTimeEntryId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Ctrl Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_inv_ctrl_owner", "inv_ctrl_owner@test.com", "Ctrl Owner", "owner"));

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
                              "Ctrl Test Corp",
                              "ctrltest@test.com",
                              "+1-555-0500",
                              "CTC-001",
                              "Test customer",
                              memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project
                      var project =
                          new Project("Ctrl Test Project", "Project for ctrl tests", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      // Link project to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Task
                      var task =
                          new Task(
                              projectId,
                              "API Development",
                              "Build REST API",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // Billable time entry 1
                      var te1 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 15),
                              120,
                              true,
                              null,
                              "Backend work");
                      te1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te1 = timeEntryRepository.save(te1);
                      timeEntryId1 = te1.getId();

                      // Billable time entry 2
                      var te2 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 16),
                              90,
                              true,
                              null,
                              "Frontend work");
                      te2.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te2 = timeEntryRepository.save(te2);
                      timeEntryId2 = te2.getId();

                      // Non-billable time entry
                      var te3 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 17),
                              60,
                              false,
                              null,
                              "Meeting");
                      te3 = timeEntryRepository.save(te3);
                      nonBillableTimeEntryId = te3.getId();
                    }));
  }

  @Test
  void shouldCreateDraftInvoiceWithTimeEntries() throws Exception {
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
                      "timeEntryIds": ["%s"],
                      "dueDate": "2025-02-28",
                      "notes": "January 2025 services",
                      "paymentTerms": "Net 30"
                    }
                    """
                        .formatted(customerId, timeEntryId1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.customerName").value("Ctrl Test Corp"))
        .andExpect(jsonPath("$.orgName").value("Invoice Ctrl Test Org"))
        .andExpect(jsonPath("$.notes").value("January 2025 services"))
        .andExpect(jsonPath("$.paymentTerms").value("Net 30"))
        .andExpect(jsonPath("$.lines").isArray())
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].projectName").value("Ctrl Test Project"))
        .andExpect(jsonPath("$.subtotal").isNumber());
  }

  @Test
  void shouldReturn404WhenCustomerNotFound() throws Exception {
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
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectNonBillableTimeEntries() throws Exception {
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
                        .formatted(customerId, nonBillableTimeEntryId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldUpdateDraftAndRecomputeTotals() throws Exception {
    // First create a draft
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
                          "currency": "ZAR",
                          "timeEntryIds": ["%s"],
                          "notes": "Initial"
                        }
                        """
                            .formatted(customerId, timeEntryId2)))
            .andExpect(status().isCreated())
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update it
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dueDate": "2025-03-15",
                      "notes": "Updated notes",
                      "paymentTerms": "Net 45",
                      "taxAmount": 500.00
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("Updated notes"))
        .andExpect(jsonPath("$.paymentTerms").value("Net 45"))
        .andExpect(jsonPath("$.dueDate").value("2025-03-15"))
        .andExpect(jsonPath("$.taxAmount").value(500.0));
  }

  @Test
  void shouldRejectUpdateOnNonDraftInvoice() throws Exception {
    // Create and approve an invoice (via entity directly to set up state)
    var invoiceIdHolder = new String[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Ctrl Test Corp",
                              "ctrltest@test.com",
                              null,
                              "Invoice Ctrl Test Org",
                              memberIdOwner);
                      invoice = invoiceRepository.save(invoice);
                      invoice.approve("INV-TEST-001", memberIdOwner);
                      invoice = invoiceRepository.save(invoice);
                      invoiceIdHolder[0] = invoice.getId().toString();
                    }));

    // Try to update the approved invoice via API
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceIdHolder[0])
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "notes": "Should fail"
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldDeleteDraftInvoice() throws Exception {
    // Create a draft with no time entries
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

    // Delete it
    mockMvc
        .perform(delete("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's gone
    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldGetInvoiceDetailWithEnrichedLines() throws Exception {
    // Create a draft with time entries
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
                          "currency": "ZAR",
                          "notes": "Detail test"
                        }
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Get detail
    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.customerName").value("Ctrl Test Corp"))
        .andExpect(jsonPath("$.lines").isArray());
  }

  @Test
  void shouldListInvoicesWithFilters() throws Exception {
    // List all
    mockMvc
        .perform(get("/api/invoices").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());

    // List by customerId
    mockMvc
        .perform(get("/api/invoices").param("customerId", customerId.toString()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());

    // List by status
    mockMvc
        .perform(get("/api/invoices").param("status", "DRAFT").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void shouldAddAndUpdateAndDeleteLineItem() throws Exception {
    // Create a draft
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
    var addResult =
        mockMvc
            .perform(
                post("/api/invoices/" + invoiceId + "/lines")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "projectId": "%s",
                          "description": "Setup fee",
                          "quantity": 1.0000,
                          "unitPrice": 5000.00,
                          "sortOrder": 100
                        }
                        """
                            .formatted(projectId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.lines[0].description").value("Setup fee"))
            .andExpect(jsonPath("$.subtotal").value(5000.0))
            .andReturn();

    String lineId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.lines[0].id");

    // Update the line item
    mockMvc
        .perform(
            put("/api/invoices/" + invoiceId + "/lines/" + lineId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Updated setup fee",
                      "quantity": 2.0000,
                      "unitPrice": 3000.00,
                      "sortOrder": 100
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].description").value("Updated setup fee"))
        .andExpect(jsonPath("$.subtotal").value(6000.0));

    // Delete the line item
    mockMvc
        .perform(delete("/api/invoices/" + invoiceId + "/lines/" + lineId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify subtotal is now zero
    mockMvc
        .perform(get("/api/invoices/" + invoiceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(0))
        .andExpect(jsonPath("$.subtotal").value(0.0));
  }

  @Test
  void shouldRejectLineItemOperationsOnNonDraftInvoice() throws Exception {
    // Create and approve an invoice
    var invoiceIdHolder = new String[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Ctrl Test Corp",
                              "ctrltest@test.com",
                              null,
                              "Invoice Ctrl Test Org",
                              memberIdOwner);
                      invoice = invoiceRepository.save(invoice);
                      invoice.approve("INV-TEST-002", memberIdOwner);
                      invoice = invoiceRepository.save(invoice);
                      invoiceIdHolder[0] = invoice.getId().toString();
                    }));

    // Try to add a line item to the approved invoice
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceIdHolder[0] + "/lines")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Should fail",
                      "quantity": 1.0000,
                      "unitPrice": 1000.00,
                      "sortOrder": 0
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldDenyMemberAccessToInvoiceEndpoints() throws Exception {
    // Members should be denied access to all invoice endpoints (admin/owner only)
    mockMvc.perform(get("/api/invoices").with(memberJwt())).andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/invoices")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "currency": "ZAR"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/invoices/" + UUID.randomUUID())
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Should fail"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/invoices/" + UUID.randomUUID()).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_inv_ctrl_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_inv_ctrl_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

  @Autowired private InvoiceRepository invoiceRepository;

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
