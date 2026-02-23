package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
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
 * Integration tests verifying the task sync pipeline: staff API call -> PortalTaskEvent ->
 * PortalEventHandler -> portal read-model rows, and the portal task list endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTaskSyncIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_portal_task_sync_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String projectId;
  private String customerId;
  private String portalToken;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Task Sync Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(
        ORG_ID, "user_task_sync_owner", "task_sync_owner@test.com", "Task Sync Owner", "owner");

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    projectId = createProject("Task Sync Project", "For portal task sync tests");
    customerId = createCustomer("Task Sync Customer", "task_sync_cust@test.com");
    linkProjectToCustomer(customerId, projectId);

    // Create portal contact and issue token
    UUID customerUUID = UUID.fromString(customerId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                portalContactService.createContact(
                    ORG_ID,
                    customerUUID,
                    "task-portal@test.com",
                    "Task Contact",
                    PortalContact.ContactRole.PRIMARY));
    portalToken = portalJwtService.issueToken(customerUUID, ORG_ID);
  }

  @Test
  void createTask_syncsToPortalReadModel() throws Exception {
    String taskId = createTask(projectId, "Task Alpha");

    var tasks = readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(tasks)
        .anyMatch(t -> t.id().equals(UUID.fromString(taskId)) && "Task Alpha".equals(t.name()));
  }

  @Test
  void updateTask_updatesPortalReadModel() throws Exception {
    String taskId = createTask(projectId, "Task Beta");

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "Task Beta Updated", "status": "IN_PROGRESS", "priority": "HIGH"}
                    """))
        .andExpect(status().isOk());

    var tasks = readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(tasks)
        .anyMatch(
            t -> t.id().equals(UUID.fromString(taskId)) && "Task Beta Updated".equals(t.name()));
  }

  @Test
  void deleteTask_removesFromPortalReadModel() throws Exception {
    String taskId = createTask(projectId, "Task Gamma");
    assertThat(readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID))
        .anyMatch(t -> t.id().equals(UUID.fromString(taskId)));

    mockMvc
        .perform(delete("/api/tasks/" + taskId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    assertThat(readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID))
        .noneMatch(t -> t.id().equals(UUID.fromString(taskId)));
  }

  @Test
  void portalEndpoint_listsTasks() throws Exception {
    String taskId = createTask(projectId, "Task Delta");

    mockMvc
        .perform(
            get("/portal/projects/" + projectId + "/tasks")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Task Delta')]").exists());
  }

  @Test
  void portalEndpoint_empty_whenNoTasks() throws Exception {
    // Create separate project with no tasks for isolation
    String emptyProjectId = createProject("Empty Task Project", "no tasks");
    linkProjectToCustomer(customerId, emptyProjectId);

    mockMvc
        .perform(
            get("/portal/projects/" + emptyProjectId + "/tasks")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void portalEndpoint_returns401_withoutToken() throws Exception {
    mockMvc
        .perform(get("/portal/projects/" + projectId + "/tasks"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void portalEndpoint_returns404_forUnlinkedProject() throws Exception {
    String unlinkedProjectId = createProject("Unlinked Project", "not linked to customer");

    mockMvc
        .perform(
            get("/portal/projects/" + unlinkedProjectId + "/tasks")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void taskNotLinkedToCustomer_notSyncedToPortal() throws Exception {
    // Create a project NOT linked to any customer
    String unlinkedProjectId = createProject("Unlinked Task Project", "no customer");

    String taskId = createTask(unlinkedProjectId, "Orphan Task");

    // This task's project has no customer link — should NOT appear in portal_tasks
    var tasks = readModelRepo.findTasksByProject(UUID.fromString(unlinkedProjectId), ORG_ID);
    assertThat(tasks).noneMatch(t -> t.id().equals(UUID.fromString(taskId)));
  }

  // --- Helpers ---

  private String createProject(String name, String description) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "%s"}
                        """
                            .formatted(name, description)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    var id = extractIdFromLocation(result);
    transitionCustomerToActive(id);
    return id;
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.completeChecklistItems(mockMvc, customerId, ownerJwt());
  }

  private void linkProjectToCustomer(String customerId, String projectId) throws Exception {
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());
  }

  private String createTask(String projectId, String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "priority": "MEDIUM"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
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
        .jwt(
            j -> j.subject("user_task_sync_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
