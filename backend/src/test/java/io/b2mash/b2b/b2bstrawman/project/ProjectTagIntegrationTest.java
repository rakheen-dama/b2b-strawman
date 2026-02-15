package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTagIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proj_tag_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String tagId1;
  private String tagId2;
  private String projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project Tag Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_pt_owner", "pt_owner@test.com", "PT Owner", "owner");

    // Create tags
    tagId1 = createTag("Proj Urgent", "#EF4444");
    tagId2 = createTag("Proj VIP", "#3B82F6");

    // Create project
    projectId = createProject("Tag Test Project");
  }

  @Test
  void shouldSetTagsOnProject() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tags")
                .with(ownerJwt())
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s"]}
                    """
                        .formatted(tagId1)))
        .andExpect(status().isOk());

    // Get tags
    mockMvc
        .perform(get("/api/projects/" + projectId + "/tags").with(ownerJwt()))
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
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tagIds": ["%s", "%s"]}
                    """
                        .formatted(tagId1, tagId2)))
        .andExpect(status().isOk());

    // Filter by single tag slug
    mockMvc
        .perform(get("/api/projects").with(ownerJwt()).param("tags", "proj_urgent"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Project')]").exists())
        .andExpect(jsonPath("$[?(@.name == 'Untagged Project')]").doesNotExist());

    // Filter by both tag slugs (AND logic)
    mockMvc
        .perform(get("/api/projects").with(ownerJwt()).param("tags", "proj_urgent,proj_vip"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Tag Test Project')]").exists());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pt_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String createTag(String name, String color) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/tags")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "color": "%s"}
                        """
                            .formatted(name, color)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String createProject(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
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

  private void syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
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
        .andExpect(status().isCreated());
  }
}
