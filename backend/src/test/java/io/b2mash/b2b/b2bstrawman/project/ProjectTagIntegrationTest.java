package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
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
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTagIntegrationTest {
  private static final String ORG_ID = "org_proj_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String tagId1;
  private String tagId2;
  private String projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Tag Test Org", null);
    TestMemberHelper.syncMemberQuietly(
        mockMvc, ORG_ID, "user_pt_owner", "pt_owner@test.com", "PT Owner", "owner");

    // Create tags
    tagId1 =
        TestEntityHelper.createTag(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"), "Proj Urgent", "#EF4444");
    tagId2 =
        TestEntityHelper.createTag(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"), "Proj VIP", "#3B82F6");

    // Create project
    projectId =
        TestEntityHelper.createProject(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"), "Tag Test Project");
  }

  @Test
  void shouldSetTagsOnProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
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
  void shouldGetTagsForProject() throws Exception {
    // Set tags first
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
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
            get("/api/projects/" + projectId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Proj Urgent"));
  }

  @Test
  void shouldFilterProjectsByTags() throws Exception {
    // Create a second project
    String projectId2 = createProject("Untagged Project");

    // Tag only the first project with both tags
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tags")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk());

    // Filter by single tag slug
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
                .param("tags", "proj_urgent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Project')]").exists())
        .andExpect(jsonPath("$[?(@.name == 'Untagged Project')]").doesNotExist());

    // Filter by both tag slugs (AND logic)
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
                .param("tags", "proj_urgent,proj_vip"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Project')]").exists());
  }

  // --- Helpers ---

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pt_owner"))
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
}
