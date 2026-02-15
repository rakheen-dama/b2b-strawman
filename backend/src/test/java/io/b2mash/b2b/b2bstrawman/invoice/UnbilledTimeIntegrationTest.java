package io.b2mash.b2b.b2bstrawman.invoice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class UnbilledTimeIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_unbilled_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID projectId2;
  private UUID taskId;
  private UUID taskId2;
  private UUID billableEntryId1;
  private UUID billableEntryId2;
  private UUID billedEntryId;
  private UUID nonBillableEntryId;
  private UUID otherProjectEntryId;
  private UUID e2eEntryId1;
  private UUID e2eEntryId2;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Unbilled Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_unbilled_owner", "unbilled_owner@test.com", "UB Owner", "owner"));

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
                      // Customer
                      var customer =
                          new Customer("UB Corp", "ub@test.com", null, null, null, memberIdOwner);
                      customer = customerRepository.save(customer);
                      // Transition to ACTIVE for invoice creation (lifecycle guard)
                      customer.transitionLifecycle(
                          "ACTIVE", memberIdOwner, java.time.Instant.now(), null);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project 1
                      var project1 = new Project("Project Alpha", "First project", memberIdOwner);
                      project1 = projectRepository.save(project1);
                      projectId = project1.getId();

                      // Project 2
                      var project2 = new Project("Project Beta", "Second project", memberIdOwner);
                      project2 = projectRepository.save(project2);
                      projectId2 = project2.getId();

                      // Link both projects to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId2, memberIdOwner));

                      // Tasks
                      var task1 =
                          new Task(
                              projectId, "Design API", null, "MEDIUM", "TASK", null, memberIdOwner);
                      task1 = taskRepository.save(task1);
                      taskId = task1.getId();

                      var task2 =
                          new Task(
                              projectId2, "Build UI", null, "MEDIUM", "TASK", null, memberIdOwner);
                      task2 = taskRepository.save(task2);
                      taskId2 = task2.getId();

                      // Billable entry 1 (project 1) — date: Jan 15
                      var te1 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 15),
                              120,
                              true,
                              null,
                              "API work");
                      te1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te1 = timeEntryRepository.save(te1);
                      billableEntryId1 = te1.getId();

                      // Billable entry 2 (project 2) — date: Jan 20
                      var te2 =
                          new TimeEntry(
                              taskId2,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 20),
                              90,
                              true,
                              null,
                              "UI work");
                      te2.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      te2 = timeEntryRepository.save(te2);
                      billableEntryId2 = te2.getId();

                      // Create a real invoice to link the billed entry to (FK constraint)
                      var existingInvoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "UB Corp",
                              "ub@test.com",
                              null,
                              "Unbilled Test Org",
                              memberIdOwner);
                      existingInvoice = invoiceRepository.save(existingInvoice);

                      // Already billed entry (has invoice_id) — date: Jan 10
                      var te3 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 10),
                              60,
                              true,
                              null,
                              "Billed work");
                      te3.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te3 = timeEntryRepository.save(te3);
                      te3.setInvoiceId(existingInvoice.getId());
                      te3 = timeEntryRepository.save(te3);
                      billedEntryId = te3.getId();

                      // Non-billable entry — date: Jan 12
                      var te4 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 1, 12),
                              30,
                              false,
                              null,
                              "Meeting");
                      te4 = timeEntryRepository.save(te4);
                      nonBillableEntryId = te4.getId();

                      // Entry in a project NOT linked to customer
                      var unlinkedProject =
                          new Project("Unlinked Project", "Not linked", memberIdOwner);
                      unlinkedProject = projectRepository.save(unlinkedProject);
                      var unlinkedTask =
                          new Task(
                              unlinkedProject.getId(),
                              "Other task",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberIdOwner);
                      unlinkedTask = taskRepository.save(unlinkedTask);
                      var te5 =
                          new TimeEntry(
                              unlinkedTask.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 1, 18),
                              45,
                              true,
                              null,
                              "Other");
                      te5.snapshotBillingRate(new BigDecimal("2000.00"), "ZAR");
                      te5 = timeEntryRepository.save(te5);
                      otherProjectEntryId = te5.getId();

                      // Separate entries for E2E test (will be invoiced and should not
                      // affect the read-only assertion tests)
                      var teE2e1 =
                          new TimeEntry(
                              taskId,
                              memberIdOwner,
                              LocalDate.of(2025, 2, 1),
                              60,
                              true,
                              null,
                              "E2E work 1");
                      teE2e1.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      teE2e1 = timeEntryRepository.save(teE2e1);
                      e2eEntryId1 = teE2e1.getId();

                      var teE2e2 =
                          new TimeEntry(
                              taskId2,
                              memberIdOwner,
                              LocalDate.of(2025, 2, 5),
                              45,
                              true,
                              null,
                              "E2E work 2");
                      teE2e2.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      teE2e2 = timeEntryRepository.save(teE2e2);
                      e2eEntryId2 = teE2e2.getId();
                    }));
  }

  @Test
  void shouldReturnUnbilledTimeGroupedByProject() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.customerName").value("UB Corp"))
        .andExpect(jsonPath("$.projects").isArray())
        .andExpect(jsonPath("$.projects.length()").value(2))
        .andExpect(jsonPath("$.grandTotals.ZAR").exists())
        .andExpect(jsonPath("$.grandTotals.ZAR.hours").isNumber())
        .andExpect(jsonPath("$.grandTotals.ZAR.amount").isNumber());
  }

  @Test
  void shouldExcludeBilledEntries() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    // The already-billed entry should NOT appear in results
    assertFalse(body.contains(billedEntryId.toString()));
  }

  @Test
  void shouldExcludeNonBillableEntries() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    // Non-billable entry should not appear anywhere
    assertFalse(body.contains(nonBillableEntryId.toString()));
  }

  @Test
  void shouldFilterByDateRange() throws Exception {
    // Only entries on or after 2025-01-16 — should only get Project Beta (Jan 20)
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/unbilled-time")
                .param("from", "2025-01-16")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projects.length()").value(1));
  }

  @Test
  void shouldFilterByDateRangeUpperBound() throws Exception {
    // Only entries on or before 2025-01-17 — should only get Project Alpha (Jan 15)
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/unbilled-time")
                .param("to", "2025-01-17")
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projects.length()").value(1))
        .andExpect(jsonPath("$.projects[0].projectName").value("Project Alpha"));
  }

  @Test
  void shouldExcludeEntriesFromUnlinkedProjects() throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertFalse(body.contains(otherProjectEntryId.toString()));
  }

  @Test
  void shouldReturn404ForNonExistentCustomer() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + UUID.randomUUID() + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldDenyMemberAccessToUnbilledTime() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectCreateDraftWithUnlinkedProjectEntry() throws Exception {
    // otherProjectEntryId is in a project NOT linked to the customer — should be rejected
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
                        .formatted(customerId, otherProjectEntryId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldCreateDraftFromUnbilledTimeAndApprove() throws Exception {
    // Step 1: Fetch unbilled time
    mockMvc
        .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
        .andExpect(status().isOk());

    // Step 2: Create draft from separate E2E entries (not the ones used by read-only tests)
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
                          "timeEntryIds": ["%s", "%s"]
                        }
                        """
                            .formatted(customerId, e2eEntryId1, e2eEntryId2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lines.length()").value(2))
            .andReturn();

    String invoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Step 3: Approve
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.invoiceNumber").isNotEmpty());

    // Step 4: Verify entries are now billed (should not appear in unbilled query)
    var unbilledResult =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/unbilled-time").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String unbilledBody = unbilledResult.getResponse().getContentAsString();
    assertFalse(unbilledBody.contains(e2eEntryId1.toString()));
    assertFalse(unbilledBody.contains(e2eEntryId2.toString()));
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_unbilled_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_unbilled_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
