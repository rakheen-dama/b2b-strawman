package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProjectCustomFieldIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_project_cf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project CF Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_pcf_owner", "pcf_owner@test.com", "Owner", "owner");
    syncMember(ORG_ID, "user_pcf_admin", "pcf_admin@test.com", "Admin", "admin");

    // Create field definitions for PROJECT entity type
    createFieldDefinition("Court", "court", "TEXT", "PROJECT");
    createFieldDefinition("Is Urgent", "is_urgent", "BOOLEAN", "PROJECT");
    createFieldDefinition(
        "Priority Level",
        "priority_level",
        "DROPDOWN",
        "PROJECT",
        """
        [{"value":"low","label":"Low"},{"value":"medium","label":"Medium"},{"value":"high","label":"High"}]
        """);
  }

  @Test
  void shouldCreateProjectWithCustomFields() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CF Project",
                      "description": "Test",
                      "customFields": {
                        "court": "High Court",
                        "is_urgent": true
                      }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("CF Project"))
        .andExpect(jsonPath("$.customFields.court").value("High Court"))
        .andExpect(jsonPath("$.customFields.is_urgent").value(true));
  }

  @Test
  void shouldUpdateProjectCustomFields() throws Exception {
    // Create a project first
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Update CF Project", "description": "Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update with custom fields
    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update CF Project",
                      "description": "Test",
                      "customFields": {
                        "court": "Supreme Court",
                        "priority_level": "high"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.court").value("Supreme Court"))
        .andExpect(jsonPath("$.customFields.priority_level").value("high"));
  }

  @Test
  void shouldReturn400ForInvalidCustomFieldValue() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Bad CF Project",
                      "description": "Test",
                      "customFields": {
                        "is_urgent": "not-a-boolean"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFilterProjectsByCustomField() throws Exception {
    // Create project with specific custom field
    mockMvc
        .perform(
            post("/api/projects")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Filterable Project",
                      "description": "Test",
                      "customFields": {
                        "court": "Magistrate Court"
                      }
                    }
                    """))
        .andExpect(status().isCreated());

    // Filter by custom field
    mockMvc
        .perform(
            get("/api/projects").with(ownerJwt()).param("customField[court]", "Magistrate Court"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customFields.court == 'Magistrate Court')]").exists());
  }

  @Test
  void shouldApplyFieldGroupsToProject() throws Exception {
    // Create a field group first
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "PROJECT",
                          "name": "Project CF Test Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "FG Project", "description": "Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

    // Apply field groups
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/field-groups")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isOk());
  }

  // --- Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pcf_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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

  private void createFieldDefinition(String name, String slug, String fieldType, String entityType)
      throws Exception {
    createFieldDefinition(name, slug, fieldType, entityType, null);
  }

  private void createFieldDefinition(
      String name, String slug, String fieldType, String entityType, String optionsJson)
      throws Exception {
    String optionsPart = optionsJson != null ? ", \"options\": " + optionsJson.trim() : "";
    String body =
        """
        {
          "entityType": "%s",
          "name": "%s",
          "fieldType": "%s",
          "required": false,
          "sortOrder": 0%s
        }
        """
            .formatted(entityType, name, fieldType, optionsPart);

    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }
}
