package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestTemplateControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_req_tmpl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String createdTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Request Template Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_req_owner", "req_owner@test.com", "Req Owner", "owner");
    syncMember(ORG_ID, "user_req_member", "req_member@test.com", "Req Member", "member");
  }

  @Test
  @Order(1)
  void shouldListPlatformTemplatesSeededDuringProvisioning() throws Exception {
    mockMvc
        .perform(get("/api/request-templates").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].source").value("PLATFORM"));
  }

  @Test
  @Order(2)
  void shouldCreateCustomTemplateWithItems() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/request-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                {
                  "name": "Custom Onboarding Docs",
                  "description": "Documents for new client onboarding",
                  "items": [
                    {
                      "name": "ID Document",
                      "description": "Government-issued photo ID",
                      "responseType": "FILE_UPLOAD",
                      "required": true,
                      "fileTypeHints": "PDF, Images",
                      "sortOrder": 1
                    },
                    {
                      "name": "Proof of Address",
                      "description": "Utility bill or bank statement",
                      "responseType": "FILE_UPLOAD",
                      "required": true,
                      "fileTypeHints": "PDF",
                      "sortOrder": 2
                    }
                  ]
                }
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Custom Onboarding Docs"))
            .andExpect(jsonPath("$.source").value("CUSTOM"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("ID Document"))
            .andExpect(jsonPath("$.items[0].responseType").value("FILE_UPLOAD"))
            .andReturn();
    createdTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  @Order(3)
  void shouldGetTemplateById() throws Exception {
    mockMvc
        .perform(get("/api/request-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdTemplateId))
        .andExpect(jsonPath("$.name").value("Custom Onboarding Docs"))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  @Order(4)
  void shouldUpdateTemplate() throws Exception {
    mockMvc
        .perform(
            put("/api/request-templates/" + createdTemplateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "name": "Updated Onboarding Docs",
                  "description": "Updated description",
                  "items": [
                    {
                      "name": "Passport",
                      "description": "Valid passport",
                      "responseType": "FILE_UPLOAD",
                      "required": true,
                      "fileTypeHints": "PDF",
                      "sortOrder": 1
                    }
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Onboarding Docs"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Passport"));
  }

  @Test
  @Order(5)
  void shouldDuplicateTemplate() throws Exception {
    // First get the current name (may differ depending on test ordering/caching)
    var current =
        mockMvc
            .perform(get("/api/request-templates/" + createdTemplateId).with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String currentName = JsonPath.read(current.getResponse().getContentAsString(), "$.name");

    var result =
        mockMvc
            .perform(
                post("/api/request-templates/" + createdTemplateId + "/duplicate").with(ownerJwt()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(currentName + " (Copy)"))
            .andExpect(jsonPath("$.source").value("CUSTOM"))
            .andReturn();
    // Verify the duplicate has a different ID
    String duplicateId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    assert !duplicateId.equals(createdTemplateId);
  }

  @Test
  @Order(6)
  void shouldDeactivateTemplate() throws Exception {
    mockMvc
        .perform(delete("/api/request-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's deactivated
    mockMvc
        .perform(get("/api/request-templates/" + createdTemplateId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  @Order(7)
  void shouldFilterByActive() throws Exception {
    // Only active templates (platform packs + duplicate from test 5)
    mockMvc
        .perform(get("/api/request-templates?active=true").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5));

    // Only inactive templates (the one deactivated in test 6)
    mockMvc
        .perform(get("/api/request-templates?active=false").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @Order(10)
  void memberShouldBeAbleToListTemplates() throws Exception {
    mockMvc.perform(get("/api/request-templates").with(memberJwt())).andExpect(status().isOk());
  }

  @Test
  @Order(11)
  void memberShouldBeAbleToGetTemplateById() throws Exception {
    // Use a platform template that we know exists
    var result =
        mockMvc
            .perform(get("/api/request-templates").with(memberJwt()))
            .andExpect(status().isOk())
            .andReturn();
    String firstTemplateId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id");

    mockMvc
        .perform(get("/api/request-templates/" + firstTemplateId).with(memberJwt()))
        .andExpect(status().isOk());
  }

  @Test
  @Order(12)
  void memberShouldNotBeAbleToCreateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/request-templates")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"name": "Unauthorized", "description": "Should fail", "items": []}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(13)
  void memberShouldNotBeAbleToUpdateTemplate() throws Exception {
    mockMvc
        .perform(
            put("/api/request-templates/" + createdTemplateId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"name": "Unauthorized Update", "description": "Should fail", "items": []}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(14)
  void memberShouldNotBeAbleToDeleteTemplate() throws Exception {
    mockMvc
        .perform(delete("/api/request-templates/" + createdTemplateId).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(15)
  void memberShouldNotBeAbleToDuplicateTemplate() throws Exception {
    mockMvc
        .perform(
            post("/api/request-templates/" + createdTemplateId + "/duplicate").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(20)
  void shouldReturnNotFoundForNonExistentTemplate() throws Exception {
    mockMvc
        .perform(
            get("/api/request-templates/00000000-0000-0000-0000-000000000000").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(21)
  void platformTemplatesShouldHavePackId() throws Exception {
    var result = mockMvc.perform(get("/api/request-templates").with(ownerJwt())).andReturn();
    String content = result.getResponse().getContentAsString();
    // Platform templates have packId set
    List<String> packIds = JsonPath.read(content, "$[?(@.source == 'PLATFORM')].packId");
    assert !packIds.isEmpty();
    assert packIds.stream().allMatch(p -> p != null && !p.isEmpty());
  }

  @Test
  @Order(22)
  void platformTemplatesShouldHaveItems() throws Exception {
    var result = mockMvc.perform(get("/api/request-templates").with(ownerJwt())).andReturn();
    String content = result.getResponse().getContentAsString();
    // All platform templates should have at least one item
    List<List<?>> itemLists = JsonPath.read(content, "$[?(@.source == 'PLATFORM')].items");
    assert itemLists.stream().allMatch(items -> !items.isEmpty());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_req_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_req_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
