package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
  private static final String ORG_ID = "org_portal_task_sync_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
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
    provisioningService.provisionTenant(ORG_ID, "Portal Task Sync Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_task_sync_owner",
        "task_sync_owner@test.com",
        "Task Sync Owner",
        "owner");

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    projectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            "Task Sync Project",
            "For portal task sync tests");
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            "Task Sync Customer",
            "task_sync_cust@test.com");
    transitionCustomerToActive(customerId);
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
    String taskId =
        TestEntityHelper.createTask(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            projectId,
            "Task Alpha");

    var tasks = readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(tasks)
        .anyMatch(t -> t.id().equals(UUID.fromString(taskId)) && "Task Alpha".equals(t.name()));
  }

  @Test
  void updateTask_updatesPortalReadModel() throws Exception {
    String taskId =
        TestEntityHelper.createTask(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            projectId,
            "Task Beta");

    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"))
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
    String taskId =
        TestEntityHelper.createTask(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            projectId,
            "Task Gamma");
    assertThat(readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID))
        .anyMatch(t -> t.id().equals(UUID.fromString(taskId)));

    mockMvc
        .perform(
            delete("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner")))
        .andExpect(status().isNoContent());

    assertThat(readModelRepo.findTasksByProject(UUID.fromString(projectId), ORG_ID))
        .noneMatch(t -> t.id().equals(UUID.fromString(taskId)));
  }

  @Test
  void portalEndpoint_listsTasks() throws Exception {
    String taskId =
        TestEntityHelper.createTask(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            projectId,
            "Task Delta");

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
    String emptyProjectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            "Empty Task Project",
            "no tasks");
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
    String unlinkedProjectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            "Unlinked Project",
            "not linked to customer");

    mockMvc
        .perform(
            get("/portal/projects/" + unlinkedProjectId + "/tasks")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void taskNotLinkedToCustomer_notSyncedToPortal() throws Exception {
    // Create a project NOT linked to any customer
    String unlinkedProjectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            "Unlinked Task Project",
            "no customer");

    String taskId =
        TestEntityHelper.createTask(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"),
            unlinkedProjectId,
            "Orphan Task");

    // This task's project has no customer link — should NOT appear in portal_tasks
    var tasks = readModelRepo.findTasksByProject(UUID.fromString(unlinkedProjectId), ORG_ID);
    assertThat(tasks).noneMatch(t -> t.id().equals(UUID.fromString(taskId)));
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    TestChecklistHelper.transitionToActive(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner"));
  }

  private void linkProjectToCustomer(String customerId, String projectId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_task_sync_owner")))
        .andExpect(status().isCreated());
  }
}
