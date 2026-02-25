package io.b2mash.b2b.b2bstrawman.fielddefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
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
class AutoApplyOnCreationIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_auto_apply_creation_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private FieldGroupRepository fieldGroupRepository;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Auto Apply Creation Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_aac_owner", "aac_owner@test.com", "AAC Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void new_customer_gets_auto_apply_groups() throws Exception {
    // Create a CUSTOMER field group with autoApply=true
    var createGroupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "CUSTOMER",
                          "name": "Customer Auto Group",
                          "description": "Auto-applied to new customers",
                          "sortOrder": 1,
                          "autoApply": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.autoApply").value(true))
            .andReturn();

    String groupId = JsonPath.read(createGroupResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a new customer via API
    var createCustomerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "New Auto Customer",
                          "email": "new_auto_customer@test.com"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String customerId =
        JsonPath.read(createCustomerResult.getResponse().getContentAsString(), "$.id");
    UUID customerUuid = UUID.fromString(customerId);

    // Verify customer has the auto-apply group
    runInTenant(
        () -> {
          Customer customer = customerRepository.findById(customerUuid).orElseThrow();
          assertThat(customer.getAppliedFieldGroups()).contains(groupUuid);
        });
  }

  @Test
  void new_project_gets_auto_apply_groups() throws Exception {
    // Create a PROJECT field group with autoApply=true
    var createGroupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "PROJECT",
                          "name": "Project Auto Group",
                          "description": "Auto-applied to new projects",
                          "sortOrder": 1,
                          "autoApply": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.autoApply").value(true))
            .andReturn();

    String groupId = JsonPath.read(createGroupResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a new project via API
    var createProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "New Auto Project",
                          "description": "Testing auto-apply"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String projectId =
        JsonPath.read(createProjectResult.getResponse().getContentAsString(), "$.id");
    UUID projectUuid = UUID.fromString(projectId);

    // Verify project has the auto-apply group
    runInTenant(
        () -> {
          Project project = projectRepository.findById(projectUuid).orElseThrow();
          assertThat(project.getAppliedFieldGroups()).contains(groupUuid);
        });
  }

  @Test
  void new_task_gets_auto_apply_groups() throws Exception {
    // Create a TASK field group with autoApply=true
    var createGroupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "name": "Task Auto Group",
                          "description": "Auto-applied to new tasks",
                          "sortOrder": 1,
                          "autoApply": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.autoApply").value(true))
            .andReturn();

    String groupId = JsonPath.read(createGroupResult.getResponse().getContentAsString(), "$.id");
    UUID groupUuid = UUID.fromString(groupId);

    // Create a project first (tasks require a project)
    var createProjectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Task Auto Project",
                          "description": "For task auto-apply test"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String projectId =
        JsonPath.read(createProjectResult.getResponse().getContentAsString(), "$.id");

    // Create a new task via API
    var createTaskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "New Auto Task",
                          "priority": "MEDIUM"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String taskId = JsonPath.read(createTaskResult.getResponse().getContentAsString(), "$.id");
    UUID taskUuid = UUID.fromString(taskId);

    // Verify task has the auto-apply group
    runInTenant(
        () -> {
          Task task = taskRepository.findById(taskUuid).orElseThrow();
          assertThat(task.getAppliedFieldGroups()).contains(groupUuid);
        });
  }

  @Test
  void entity_with_no_auto_apply_groups_has_empty_list() throws Exception {
    // Disable auto-apply on all CUSTOMER groups in this tenant so we test the no-auto-apply path
    runInTenant(
        () -> {
          var customerGroups =
              fieldGroupRepository.findByEntityTypeAndAutoApplyTrueAndActiveTrue(
                  EntityType.CUSTOMER);
          for (var g : customerGroups) {
            g.setAutoApply(false);
            fieldGroupRepository.save(g);
          }
        });

    var createCustomerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "No Auto Customer",
                          "email": "no_auto_unique_customer@test.com"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String customerId =
        JsonPath.read(createCustomerResult.getResponse().getContentAsString(), "$.id");
    UUID customerUuid = UUID.fromString(customerId);

    // Verify customer has no auto-applied field groups
    runInTenant(
        () -> {
          Customer customer = customerRepository.findById(customerUuid).orElseThrow();
          assertThat(
                  customer.getAppliedFieldGroups() != null
                      ? customer.getAppliedFieldGroups()
                      : List.of())
              .isEmpty();
        });

    // Re-enable auto-apply on CUSTOMER groups for other tests
    runInTenant(
        () -> {
          var customerGroups =
              fieldGroupRepository.findByEntityTypeAndActiveTrueOrderBySortOrder(
                  EntityType.CUSTOMER);
          for (var g : customerGroups) {
            g.setAutoApply(true);
            fieldGroupRepository.save(g);
          }
        });
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_aac_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  // --- Tenant Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
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
}
