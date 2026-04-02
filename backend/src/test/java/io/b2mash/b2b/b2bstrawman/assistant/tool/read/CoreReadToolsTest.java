package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import java.time.LocalDate;
import java.util.List;
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
class CoreReadToolsTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_core_read_tools_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectService projectService;
  @Autowired private CustomerService customerService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;

  @Autowired private ListProjectsTool listProjectsTool;
  @Autowired private GetProjectTool getProjectTool;
  @Autowired private ListCustomersTool listCustomersTool;
  @Autowired private GetCustomerTool getCustomerTool;
  @Autowired private ListTasksTool listTasksTool;
  @Autowired private GetMyTasksTool getMyTasksTool;
  @Autowired private GetTimeSummaryTool getTimeSummaryTool;
  @Autowired private AssistantToolRegistry assistantToolRegistry;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID projectId;
  private UUID customerId;
  private UUID taskId;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Core Read Tools Test Org", null);

    var memberIdStr =
        syncMember(ORG_ID, "user_crt_owner", "crt_owner@test.com", "CRT Owner", "owner");
    memberIdOwner = UUID.fromString(memberIdStr);

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var actor = new ActorContext(memberIdOwner, "owner");

              // Create a customer
              var customer =
                  customerService.createCustomer(
                      "Test Customer", "crt_customer@test.com", null, null, null, memberIdOwner);
              customerId = customer.getId();

              // Create a project
              var project =
                  projectService.createProject("Test Project", "A test project", memberIdOwner);
              projectId = project.getId();

              // Create a task
              var task =
                  taskService.createTask(
                      projectId, "Test Task", null, "MEDIUM", "TASK", null, actor);
              taskId = task.getId();

              // Create a time entry
              timeEntryService.createTimeEntry(
                  taskId, LocalDate.now(), 60, true, null, "Test time entry", actor);
            });
  }

  @Test
  void listProjectsToolReturnsSeededProjects() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result = (List<Map<String, Object>>) listProjectsTool.execute(Map.of(), ctx);
          assertThat(result).isNotEmpty();
          assertThat(result).anyMatch(p -> "Test Project".equals(p.get("name")));
        });
  }

  @Test
  void getProjectToolByIdReturnsProjectDetails() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getProjectTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result).containsKey("id");
          assertThat(result.get("name")).isEqualTo("Test Project");
          assertThat(result).containsKey("status");
          assertThat(result).containsKey("createdAt");
        });
  }

  @Test
  void listCustomersToolReturnsCustomers() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result = (List<Map<String, Object>>) listCustomersTool.execute(Map.of(), ctx);
          assertThat(result).isNotEmpty();
          assertThat(result).anyMatch(c -> "Test Customer".equals(c.get("name")));
        });
  }

  @Test
  void getCustomerToolByIdReturnsDetails() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getCustomerTool.execute(Map.of("customerId", customerId.toString()), ctx);
          assertThat(result.get("name")).isEqualTo("Test Customer");
          assertThat(result.get("email")).isEqualTo("crt_customer@test.com");
          assertThat(result).containsKey("createdAt");
        });
  }

  @Test
  void listTasksToolFiltersByProjectId() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (List<Map<String, Object>>)
                  listTasksTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result).isNotEmpty();
          assertThat(result).anyMatch(t -> "Test Task".equals(t.get("title")));
        });
  }

  @Test
  void getMyTasksToolReturnsCurrentUserTasks() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) getMyTasksTool.execute(Map.of(), ctx);
          assertThat(result).containsKey("assigned");
          assertThat(result).containsKey("unassigned");
        });
  }

  @Test
  void getTimeSummaryToolReturnsProjectSummary() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getTimeSummaryTool.execute(Map.of("projectId", projectId.toString()), ctx);
          assertThat(result.get("totalMinutes")).isNotNull();
          assertThat(((Number) result.get("totalMinutes")).longValue()).isGreaterThanOrEqualTo(60L);
          assertThat(result).containsKey("byMember");
        });
  }

  @Test
  void assistantToolRegistryDiscoversAllToolBeans() {
    var tools = assistantToolRegistry.getToolsForUser(Set.of());
    assertThat(tools.size()).isGreaterThanOrEqualTo(7);
  }

  @Test
  void getToolDefinitionsReturnsCorrectNamesAndSchemas() {
    var definitions = assistantToolRegistry.getToolDefinitions(Set.of());
    assertThat(definitions.size()).isGreaterThanOrEqualTo(7);
    for (var def : definitions) {
      assertThat(def.name()).isNotNull();
      assertThat(def.description()).isNotNull();
      assertThat(def.inputSchema()).isNotNull();
      assertThat(def.inputSchema()).containsKey("type");
    }
  }

  // --- Helpers ---

  private TenantToolContext buildContext() {
    return new TenantToolContext(tenantSchema, memberIdOwner, "owner", Set.of());
  }

  private void runInTenantScope(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(RequestScopes.CAPABILITIES, Set.of())
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
