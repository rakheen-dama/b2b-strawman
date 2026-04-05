package io.b2mash.b2b.b2bstrawman.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
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
class TaskTagIntegrationTest {
  private static final String ORG_ID = "org_task_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String tagId1;
  private String tagId2;
  private String projectId;
  private String taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Task Tag Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_tt_owner", "tt_owner@test.com", "TT Owner", "owner");

    // Create tags
    tagId1 =
        TestEntityHelper.createTag(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"), "Task Urgent", "#EF4444");
    tagId2 =
        TestEntityHelper.createTag(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"), "Task Review", "#3B82F6");

    // Create project and task
    projectId = createProject("Task Tag Test Project");
    taskId = createTask(projectId, "Tag Test Task");
  }

  @Test
  void shouldSetTagsOnTask() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void shouldGetTagsForTask() throws Exception {
    // Set tags first
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s"]}
                    """
                        .formatted(tagId1)))
        .andExpect(status().isOk());

    // Get tags
    mockMvc
        .perform(
            get("/api/tasks/" + taskId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Task Urgent"));
  }

  @Test
  void shouldFilterTasksByTags() throws Exception {
    // Create a second task
    String taskId2 = createTask(projectId, "Untagged Task");

    // Tag only the first task
    mockMvc
        .perform(
            post("/api/tasks/" + taskId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, this.tagId2)))
        .andExpect(status().isOk());

    // Filter by tag slug
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                .param("tags", "task_urgent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.title == 'Tag Test Task')]").exists())
        .andExpect(jsonPath("$[?(@.title == 'Untagged Task')]").doesNotExist());

    // Filter by both tag slugs (AND logic)
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                .param("tags", "task_urgent,task_review"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.title == 'Tag Test Task')]").exists());
  }

  // --- Helpers ---

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "Test project"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createTask(String projectId, String title) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/tasks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_tt_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title": "%s", "priority": "MEDIUM"}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
