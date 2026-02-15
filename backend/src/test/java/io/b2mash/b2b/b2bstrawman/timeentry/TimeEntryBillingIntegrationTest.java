package io.b2mash.b2b.b2bstrawman.timeentry;

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

/**
 * Integration tests for time entry billing status filtering and response enrichment (Epic 86A).
 * Verifies that billing status (UNBILLED/BILLED/NON_BILLABLE) is correctly derived and that the
 * list endpoint supports filtering by billing status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeEntryBillingIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_te_billing_test";

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

  // Time entry IDs for test data
  private UUID billableUnbilledEntryId;
  private UUID billableToBeInvoicedEntryId;
  private UUID nonBillableEntryId;

  // Invoice data
  private String invoiceId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "TE Billing Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                "user_te_billing_owner", "te_billing_owner@test.com", "Billing Owner", "owner"));

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
                      var customer =
                          new Customer(
                              "Billing Test Corp",
                              "billing@test.com",
                              "+1-555-0700",
                              "BTC-001",
                              "Test customer for billing",
                              memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var project =
                          new Project(
                              "Billing Test Project", "Project for billing tests", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      var task =
                          new Task(
                              projectId,
                              "Billing Work",
                              "Billing test task",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // Create 3 time entries: 1 billable (to remain unbilled), 1 billable (to be
                      // invoiced), 1 non-billable
                      var unbilled =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 6, 1),
                              60,
                              true,
                              null,
                              "Unbilled billable work");
                      unbilled.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      unbilled = timeEntryRepository.save(unbilled);
                      billableUnbilledEntryId = unbilled.getId();

                      var toInvoice =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 6, 2),
                              120,
                              true,
                              null,
                              "Work to be invoiced");
                      toInvoice.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      toInvoice = timeEntryRepository.save(toInvoice);
                      billableToBeInvoicedEntryId = toInvoice.getId();

                      var nonBillable =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 6, 3),
                              30,
                              false,
                              null,
                              "Internal meeting");
                      nonBillable = timeEntryRepository.save(nonBillable);
                      nonBillableEntryId = nonBillable.getId();
                    }));

    // Create a draft invoice including the "to be invoiced" time entry, then approve it
    invoiceId = createDraftWithTimeEntries(billableToBeInvoicedEntryId);
    approveInvoice(invoiceId);
  }

  @Test
  void unbilledFilterReturnsOnlyBillableEntriesWithNoInvoice() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/{taskId}/time-entries", taskId)
                .param("billingStatus", "UNBILLED")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(billableUnbilledEntryId.toString()))
        .andExpect(jsonPath("$[0].billable").value(true))
        .andExpect(jsonPath("$[0].invoiceId").doesNotExist());
  }

  @Test
  void billedFilterReturnsOnlyEntriesWithInvoice() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/{taskId}/time-entries", taskId)
                .param("billingStatus", "BILLED")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(billableToBeInvoicedEntryId.toString()))
        .andExpect(jsonPath("$[0].invoiceId").value(invoiceId));
  }

  @Test
  void nonBillableFilterReturnsOnlyNonBillableEntries() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/{taskId}/time-entries", taskId)
                .param("billingStatus", "NON_BILLABLE")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(nonBillableEntryId.toString()))
        .andExpect(jsonPath("$[0].billable").value(false));
  }

  @Test
  void billedEntryResponseIncludesInvoiceIdAndInvoiceNumber() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/{taskId}/time-entries", taskId)
                .param("billingStatus", "BILLED")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].invoiceId").value(invoiceId))
        .andExpect(jsonPath("$[0].invoiceNumber").exists())
        .andExpect(jsonPath("$[0].invoiceNumber").isString());
  }

  @Test
  void unbilledEntryResponseHasNullInvoiceFields() throws Exception {
    mockMvc
        .perform(
            get("/api/tasks/{taskId}/time-entries", taskId)
                .param("billingStatus", "UNBILLED")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].invoiceId").doesNotExist())
        .andExpect(jsonPath("$[0].invoiceNumber").doesNotExist());
  }

  @Test
  void editBilledEntryReturns409Conflict() throws Exception {
    mockMvc
        .perform(
            put("/api/time-entries/{id}", billableToBeInvoicedEntryId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "durationMinutes": 180
                    }
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteBilledEntryReturns409Conflict() throws Exception {
    mockMvc
        .perform(delete("/api/time-entries/{id}", billableToBeInvoicedEntryId).with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void noFilterReturnsAllEntries() throws Exception {
    mockMvc
        .perform(get("/api/tasks/{taskId}/time-entries", taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  // --- Helper methods ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_te_billing_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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

  private void approveInvoice(String invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/invoices/{id}/approve", invoiceId).with(ownerJwt()))
        .andExpect(status().isOk());
  }
}
