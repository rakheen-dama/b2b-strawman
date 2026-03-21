package io.b2mash.b2b.b2bstrawman.assistant.tool.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WriteToolsTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_write_tools_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectService projectService;
  @Autowired private CustomerService customerService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private AssistantToolRegistry assistantToolRegistry;

  @Autowired private CreateProjectTool createProjectTool;
  @Autowired private UpdateProjectTool updateProjectTool;
  @Autowired private CreateCustomerTool createCustomerTool;
  @Autowired private CreateTaskTool createTaskTool;
  @Autowired private UpdateTaskTool updateTaskTool;
  @Autowired private LogTimeEntryTool logTimeEntryTool;
  @Autowired private CreateInvoiceDraftTool createInvoiceDraftTool;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void provisionTenant() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Write Tools Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    var memberIdStr = syncMember(ORG_ID, "user_wt_owner", "wt_owner@test.com", "WT Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createProjectToolCreatesProjectVisibleInRepository() {
    runInTenantScope(
        () -> {
          var ctx = buildContext(Set.of("PROJECT_MANAGEMENT"));
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  createProjectTool.execute(Map.of("name", "AI Created Project"), ctx);

          assertThat(result).containsKey("id");
          assertThat(result.get("name")).isEqualTo("AI Created Project");
          assertThat(result.get("status")).isEqualTo("ACTIVE");

          // Verify persisted
          var projectId = UUID.fromString((String) result.get("id"));
          var project = projectRepository.findById(projectId);
          assertThat(project).isPresent();
          assertThat(project.get().getName()).isEqualTo("AI Created Project");
        });
  }

  @Test
  void createCustomerToolCreatesProspectCustomer() {
    runInTenantScope(
        () -> {
          var ctx = buildContext(Set.of("CUSTOMER_MANAGEMENT"));
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  createCustomerTool.execute(
                      Map.of("name", "AI Customer", "email", "ai_customer@test.com"), ctx);

          assertThat(result).containsKey("id");
          assertThat(result.get("name")).isEqualTo("AI Customer");
          assertThat(result.get("status")).isEqualTo("PROSPECT");
        });
  }

  @Test
  void createTaskToolCreatesTaskLinkedToProject() {
    runInTenantScope(
        () -> {
          // First create a project to link the task to
          var project =
              projectService.createProject("Task Host Project", "For task test", memberIdOwner);

          var ctx = buildContext(Set.of());
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  createTaskTool.execute(
                      Map.of("projectId", project.getId().toString(), "title", "AI Task"), ctx);

          assertThat(result).containsKey("id");
          assertThat(result.get("title")).isEqualTo("AI Task");
          assertThat(result.get("status")).isEqualTo("OPEN");

          // Verify persisted and linked
          var taskId = UUID.fromString((String) result.get("id"));
          var task = taskRepository.findById(taskId);
          assertThat(task).isPresent();
          assertThat(task.get().getProjectId()).isEqualTo(project.getId());
        });
  }

  @Test
  void logTimeEntryToolCreatesTimeEntryForContextMember() {
    runInTenantScope(
        () -> {
          // Create project and task for the time entry
          var project =
              projectService.createProject("Time Host Project", "For time test", memberIdOwner);
          var ctx = buildContext(Set.of());

          @SuppressWarnings("unchecked")
          var taskResult =
              (Map<String, Object>)
                  createTaskTool.execute(
                      Map.of("projectId", project.getId().toString(), "title", "Time Task"), ctx);
          var taskId = (String) taskResult.get("id");

          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  logTimeEntryTool.execute(
                      Map.of(
                          "taskId",
                          taskId,
                          "hours",
                          1.5,
                          "date",
                          LocalDate.now().toString(),
                          "description",
                          "AI logged time"),
                      ctx);

          assertThat(result).containsKey("id");
          assertThat(result.get("hours")).isEqualTo(1.5);
          assertThat(result.get("billable")).isEqualTo(true);

          // Verify persisted with correct member
          var entryId = UUID.fromString((String) result.get("id"));
          var entry = timeEntryRepository.findById(entryId);
          assertThat(entry).isPresent();
          assertThat(entry.get().getMemberId()).isEqualTo(memberIdOwner);
        });
  }

  @Test
  void updateProjectToolUpdatesProjectName() {
    runInTenantScope(
        () -> {
          // Create a project to update
          var project = projectService.createProject("Original Name", "desc", memberIdOwner);

          var ctx = buildContext(Set.of("PROJECT_MANAGEMENT"));
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  updateProjectTool.execute(
                      Map.of("projectId", project.getId().toString(), "name", "Updated Name"), ctx);

          assertThat(result.get("name")).isEqualTo("Updated Name");

          // Verify persisted
          var updated = projectRepository.findById(project.getId());
          assertThat(updated).isPresent();
          assertThat(updated.get().getName()).isEqualTo("Updated Name");
        });
  }

  @Test
  void allWriteToolsRequireConfirmation() {
    assertThat(createProjectTool.requiresConfirmation()).isTrue();
    assertThat(updateProjectTool.requiresConfirmation()).isTrue();
    assertThat(createCustomerTool.requiresConfirmation()).isTrue();
    assertThat(createTaskTool.requiresConfirmation()).isTrue();
    assertThat(updateTaskTool.requiresConfirmation()).isTrue();
    assertThat(logTimeEntryTool.requiresConfirmation()).isTrue();
    assertThat(createInvoiceDraftTool.requiresConfirmation()).isTrue();
  }

  // --- Capability enforcement tests ---

  @Test
  void createProjectToolExcludedWithoutProjectManagementCapability() {
    var toolsWithoutCapability = assistantToolRegistry.getToolsForUser(Set.of());
    assertThat(toolsWithoutCapability).noneMatch(t -> "create_project".equals(t.name()));

    var toolsWithCapability = assistantToolRegistry.getToolsForUser(Set.of("PROJECT_MANAGEMENT"));
    assertThat(toolsWithCapability).anyMatch(t -> "create_project".equals(t.name()));
  }

  @Test
  void createInvoiceDraftToolExcludedWithoutInvoicingCapability() {
    var toolsWithoutCapability = assistantToolRegistry.getToolsForUser(Set.of());
    assertThat(toolsWithoutCapability).noneMatch(t -> "create_invoice_draft".equals(t.name()));

    var toolsWithCapability = assistantToolRegistry.getToolsForUser(Set.of("INVOICING"));
    assertThat(toolsWithCapability).anyMatch(t -> "create_invoice_draft".equals(t.name()));
  }

  // --- Helpers ---

  private TenantToolContext buildContext(Set<String> capabilities) {
    return new TenantToolContext(tenantSchema, memberIdOwner, "owner", capabilities);
  }

  private void runInTenantScope(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("PROJECT_MANAGEMENT", "CUSTOMER_MANAGEMENT", "INVOICING"))
        .run(action);
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
                          "clerkOrgId": "%s", "clerkUserId": "%s",
                          "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
