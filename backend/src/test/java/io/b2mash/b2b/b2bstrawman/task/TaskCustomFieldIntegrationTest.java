package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
class TaskCustomFieldIntegrationTest {
  private static final String ORG_ID = "org_task_cf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task CF Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_tcf_owner", "tcf_owner@test.com", "Owner", "owner");

    // Create a project for tasks
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "TCF Project", "description": "Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

    // Create field definitions for TASK entity type
    createFieldDefinition("Complexity", "complexity", "NUMBER", "TASK");
    createFieldDefinition("Due Reminder", "due_reminder", "BOOLEAN", "TASK");
  }

  @Test
  void shouldCreateTaskWithCustomFields() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "CF Task",
                      "customFields": {
                        "complexity": 5,
                        "due_reminder": true
                      }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("CF Task"))
        .andExpect(jsonPath("$.customFields.complexity").value(5))
        .andExpect(jsonPath("$.customFields.due_reminder").value(true));
  }

  @Test
  void shouldUpdateTaskCustomFields() throws Exception {
    // Create task first
    var createResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "Update CF Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String taskId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update with custom fields
    mockMvc
        .perform(
            put("/api/tasks/" + taskId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Update CF Task",
                      "priority": "HIGH",
                      "status": "OPEN",
                      "customFields": {
                        "complexity": 8
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.complexity").value(8));
  }

  @Test
  void shouldReturn400ForInvalidTaskCustomField() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Bad CF Task",
                      "customFields": {
                        "complexity": "not-a-number"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFilterTasksByCustomField() throws Exception {
    // Create task with specific custom field value
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Filterable Task",
                      "customFields": {
                        "due_reminder": false
                      }
                    }
                    """))
        .andExpect(status().isCreated());

    // Filter by custom field
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .param("customField[due_reminder]", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customFields.due_reminder == false)]").exists());
  }

  @Test
  void shouldApplyFieldGroupsToTask() throws Exception {
    // Create a field group for TASK
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "TASK",
                          "name": "Task CF Test Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Create a task
    var taskResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "FG Task"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String taskId = JsonPath.read(taskResult.getResponse().getContentAsString(), "$.id");

    // Apply field groups
    mockMvc
        .perform(
            put("/api/tasks/" + taskId + "/field-groups")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isOk());
  }

  // --- Helpers ---

  private void createFieldDefinition(String name, String slug, String fieldType, String entityType)
      throws Exception {
    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "%s",
                      "name": "%s",
                      "fieldType": "%s",
                      "required": false,
                      "sortOrder": 0
                    }
                    """
                        .formatted(entityType, name, fieldType)))
        .andExpect(status().isCreated());
  }
}
