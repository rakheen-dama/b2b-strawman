package io.b2mash.b2b.b2bstrawman.project;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Comprehensive integration tests for delete protection guards and cross-entity integrity. Covers
 * project delete protection (tasks, time entries, invoices, status), task delete protection (time
 * entries), archive guards (task/time creation on archived projects), customer archive protection
 * (invoices, retainers), and cross-entity integrity (no cascading side effects).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteProtectionIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_del_prot_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String memberIdOwner;

  @BeforeAll
  void provisionTenantAndMembers() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Delete Protection Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner = syncMember(ORG_ID, "user_dp_owner", "dp_owner@test.com", "DP Owner", "owner");
  }

  // ---- Project delete protection tests ----

  @Test
  void delete_empty_active_project_succeeds() throws Exception {
    var projectId = createProject("Empty Active Project");
    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void delete_project_with_tasks_rejected() throws Exception {
    var projectId = createProject("Project With Tasks");
    createTaskInProject(projectId, "Blocking Task");

    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("task(s)")));
  }

  @Test
  void delete_project_with_time_entries_rejected() throws Exception {
    var projectId = createProject("Project With Time");
    var taskId = createTaskInProject(projectId, "Task For Time");
    createTimeEntry(taskId);

    // Project has tasks (which have time entries), so the task guard triggers first
    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("task(s)")));
  }

  @Test
  void delete_project_with_invoices_rejected() throws Exception {
    var projectId = createProject("Project With Invoices");

    // Create an active customer for the invoice
    var customerId = createActiveCustomer("Invoice Cust", "inv-dp@test.com");

    // Create an invoice for this customer via API
    var invoiceId = createInvoice(customerId);

    // Add a line item to the invoice referencing the project
    addInvoiceLineItem(invoiceId, projectId);

    // Now try to delete the project -- should be rejected because of invoice link
    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("invoice(s)")));
  }

  @Test
  void delete_completed_project_rejected() throws Exception {
    var projectId = createProject("Completed Project");
    completeProject(projectId);

    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("completed")));
  }

  @Test
  void delete_archived_project_rejected() throws Exception {
    var projectId = createProject("Archived Project");
    archiveProject(projectId);

    mockMvc
        .perform(delete("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("archived")));
  }

  // ---- Task delete protection tests ----

  @Test
  void delete_task_with_time_entries_rejected() throws Exception {
    var projectId = createProject("Project For Task Delete");
    var taskId = createTaskInProject(projectId, "Task With Time");
    createTimeEntry(taskId);

    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("time entry")));
  }

  @Test
  void delete_task_without_time_entries_succeeds() throws Exception {
    var projectId = createProject("Project For Task Delete OK");
    var taskId = createTaskInProject(projectId, "Task No Time");

    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isNoContent());
  }

  // ---- Archive guard tests (206.7) ----

  @Test
  void create_task_on_archived_project_rejected() throws Exception {
    var projectId = createProject("Archived For Task");
    archiveProject(projectId);

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Should Fail"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("archived")));
  }

  @Test
  void create_time_on_archived_project_rejected() throws Exception {
    var projectId = createProject("Archived For Time");
    var taskId = createTaskInProject(projectId, "Task Before Archive");
    archiveProject(projectId);

    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-01-15", "durationMinutes": 60, "billable": false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("archived")));
  }

  // ---- Cross-entity integrity tests (206.9) ----

  @Test
  void archive_project_does_not_auto_complete_tasks() throws Exception {
    var projectId = createProject("Archive No Complete");
    var taskId = createTaskInProject(projectId, "Open Task");

    archiveProject(projectId);

    // Reopen the project so we can read the task
    reopenProject(projectId);

    // Task should still be OPEN (not auto-completed by archive)
    mockMvc
        .perform(get("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void offboard_customer_does_not_auto_archive_projects() throws Exception {
    var customerId = createActiveCustomer("Offboard Cust", "offboard-dp@test.com");
    var projectId = createProject("Customer Project");

    // Link the project to the customer
    linkProjectToCustomer(projectId, customerId);

    // Offboard the customer (transition to OFFBOARDING)
    transitionCustomerLifecycle(customerId.toString(), "OFFBOARDING");

    // Project should still be active
    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void projects_linked_to_offboarding_customer_remain_linked() throws Exception {
    var customerId = createActiveCustomer("Linked Offboard Cust", "linked-offboard-dp@test.com");
    var projectId = createProject("Linked Project");

    linkProjectToCustomer(projectId, customerId);
    transitionCustomerLifecycle(customerId.toString(), "OFFBOARDING");

    // Project should still be accessible and active
    mockMvc
        .perform(get("/api/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  // ---- Customer archive protection tests (206.8) ----

  @Test
  void archive_customer_with_invoices_rejected() throws Exception {
    var customerId = createActiveCustomer("Inv Archive Cust", "inv-archive-dp@test.com");

    // Create an invoice for this customer via API
    createInvoice(customerId);

    // Archive should be rejected
    mockMvc
        .perform(delete("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("invoice")));
  }

  @Test
  void archive_customer_with_retainers_rejected() throws Exception {
    var customerId = createActiveCustomer("Ret Archive Cust", "ret-archive-dp@test.com");

    // Create a retainer agreement for this customer via API
    createRetainer(customerId);

    // Archive should be rejected
    mockMvc
        .perform(delete("/api/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("retainer")));
  }

  // ---- Helpers ----

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Delete protection test project"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createTaskInProject(String projectId, String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void createTimeEntry(String taskId) throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/time-entries")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"date": "2026-01-15", "durationMinutes": 60, "billable": false}
                    """))
        .andExpect(status().isCreated());
  }

  private void archiveProject(String projectId) throws Exception {
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/archive").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private void completeProject(String projectId) throws Exception {
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/complete").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private void reopenProject(String projectId) throws Exception {
    mockMvc
        .perform(patch("/api/projects/" + projectId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  private UUID createActiveCustomer(String name, String email) throws Exception {
    // Create customer via API (defaults to PROSPECT)
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s", "phone": "+1-555-0100", "idNumber": "DP-%s", "notes": "test"}
                        """
                            .formatted(name, email, UUID.randomUUID().toString().substring(0, 6))))
            .andExpect(status().isCreated())
            .andReturn();
    String customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    // Transition PROSPECT -> ONBOARDING
    transitionCustomerLifecycle(customerId, "ONBOARDING");

    // Complete all checklist items to auto-transition to ACTIVE
    TestChecklistHelper.completeChecklistItems(mockMvc, customerId, ownerJwt());

    return UUID.fromString(customerId);
  }

  private void transitionCustomerLifecycle(String customerId, String targetStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "%s"}
                    """
                        .formatted(targetStatus)))
        .andExpect(status().isOk());
  }

  private String createInvoice(UUID customerId) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerId": "%s", "currency": "ZAR"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void addInvoiceLineItem(String invoiceId, String projectId) throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/lines")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"description": "Test line", "quantity": 1, "unitPrice": 100.00, "sortOrder": 0, "projectId": "%s"}
                    """
                        .formatted(projectId)))
        .andExpect(status().isCreated());
  }

  private void createRetainer(UUID customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/retainers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "customerId": "%s",
                      "name": "Test Retainer",
                      "type": "HOUR_BANK",
                      "frequency": "MONTHLY",
                      "startDate": "2026-01-01",
                      "allocatedHours": 10,
                      "periodFee": 1000
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());
  }

  private void linkProjectToCustomer(String projectId, UUID customerId) throws Exception {
    mockMvc
        .perform(post("/api/projects/" + projectId + "/customers/" + customerId).with(ownerJwt()))
        .andExpect(status().isCreated());
  }

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
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
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
