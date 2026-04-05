package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchAndNavigationToolsTest {
  private static final String ORG_ID = "org_search_nav_tools_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectService projectService;
  @Autowired private CustomerService customerService;
  @Autowired private TaskService taskService;

  @Autowired private SearchEntitiesTool searchEntitiesTool;
  @Autowired private GetNavigationHelpTool getNavigationHelpTool;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Search Nav Tools Test Org", null);

    var memberIdStr =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_srch_owner", "srch_owner@test.com", "Search Owner", "owner");
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

              customerService.createCustomer(
                  "Searchable Customer", "search_cust@test.com", null, null, null, memberIdOwner);

              var project =
                  projectService.createProject(
                      "Searchable Project", "A project to search for", memberIdOwner);

              taskService.createTask(
                  project.getId(), "Searchable Task", null, "MEDIUM", "TASK", null, actor);
            });
  }

  @Test
  void searchEntitiesToolFindsProjectByName() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>) searchEntitiesTool.execute(Map.of("query", "Searchable"), ctx);
          @SuppressWarnings("unchecked")
          var projects = (List<Map<String, Object>>) result.get("projects");
          assertThat(projects).isNotEmpty();
        });
  }

  @Test
  void searchEntitiesToolReturnsErrorForBlankQuery() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result = (Map<String, Object>) searchEntitiesTool.execute(Map.of("query", ""), ctx);
          assertThat(result).containsKey("error");
        });
  }

  @Test
  void getNavigationHelpToolReturnsGuidanceForFeature() {
    runInTenantScope(
        () -> {
          var ctx = buildContext();
          @SuppressWarnings("unchecked")
          var result =
              (Map<String, Object>)
                  getNavigationHelpTool.execute(Map.of("feature", "invoices"), ctx);
          assertThat(result).containsKey("guidance");
          assertThat((String) result.get("guidance")).isNotEmpty();
        });
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
}
